package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.soporte.PruebaIntegracion;

/**
 * Datos de facturación creados por JDBC con valores únicos (la BD es compartida: no se toca la semilla). El periodo
 * de prueba es el de hace dos meses, que siempre está cerrado y dentro del plazo de cobro de RN-34.
 */
public abstract class PruebaFacturacionBase extends PruebaIntegracion {

    /** Escenario de un titular con un predio y su medidor de acueducto. */
    public record Escenario(Long usuarioId, Long predioId, Long medidorId) {
    }

    @Autowired
    protected EmisorFacturas emisor;

    @Autowired
    protected PlatformTransactionManager tm;

    protected static YearMonth mesPrueba() {
        return YearMonth.now(Periodos.ZONA).minusMonths(2);
    }

    protected static int periodoPrueba() {
        return Periodos.dePeriodo(mesPrueba());
    }

    protected Long funcionario(String username) {
        return jdbc.queryForObject("SELECT ID FROM SIGCA.FUNCIONARIO WHERE USERNAME = ?", Long.class, username);
    }

    protected Long circasia() {
        return jdbc.queryForObject("SELECT ID FROM SIGCA.MUNICIPIO WHERE CODIGO_DANE = '63190'", Long.class);
    }

    private Long insertar(String sql, Object... args) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[] { "ID" });
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    protected Long crearUsuario() {
        String semilla = UUID.randomUUID() + unico();
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(semilla.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String cifrado = Base64.getEncoder().encodeToString(("prueba-facturacion-" + semilla).getBytes(StandardCharsets.UTF_8));
        return insertar("""
                INSERT INTO SIGCA.USUARIO (CODIGO, TIPO_PERSONA, TIPO_IDENTIFICACION, NUMERO_IDENTIFICACION_CIFRADO,
                  NUMERO_IDENTIFICACION_HASH, NUMERO_IDENTIFICACION_MASCARA, NOMBRE_COMPLETO, CORREO, TELEFONO,
                  DIRECCION_NOTIFICACION, MUNICIPIO_ID, AUTORIZA_TRATAMIENTO_DATOS, FECHA_AUTORIZACION_DATOS, CREADO_POR)
                VALUES ('USR-00000000', 'NATURAL', 'CC', ?, ?, '******1234', 'Prueba Facturacion', ?, '3101234567',
                  'Calle 1 # 2-3 Circasia', ?, 'S', SYSTIMESTAMP, 'it.facturacion')""",
                cifrado, hash, "facturacion" + unico() + "@prueba.com", circasia());
    }

    protected Long crearPredio(int estrato) {
        return insertar("""
                INSERT INTO SIGCA.PREDIO (CODIGO_PREDIO, DIRECCION, BARRIO_VEREDA, MUNICIPIO_ID, USO, ESTRATO, CREADO_POR)
                VALUES ('PRD-00000000', ?, 'Centro', ?, 'RESIDENCIAL', ?, 'it.facturacion')""",
                "Carrera 9 # " + (System.nanoTime() % 900 + 100) + "-10", circasia(), estrato);
    }

    protected Long instalarMedidor(Long predioId, String tipo, LocalDate fecha) {
        String serial = "FAC" + tipo.charAt(0) + "-" + unico() + (System.nanoTime() % 1000);
        return insertar("""
                INSERT INTO SIGCA.MEDIDOR (SERIAL, PREDIO_ID, TIPO_SERVICIO, MARCA, FECHA_INSTALACION, LECTURA_INICIAL, CREADO_POR)
                VALUES (?, ?, ?, 'Prueba', ?, 0, 'it.facturacion')""", serial, predioId, tipo, Date.valueOf(fecha));
    }

    protected Long crearContrato(Long usuarioId, Long predioId, String tipo, Long medidorId, LocalDate vinculacion) {
        return insertar("""
                INSERT INTO SIGCA.CONTRATO_SERVICIO (NUMERO_CONTRATO, USUARIO_ID, PREDIO_ID, TIPO_SERVICIO, MEDIDOR_ID,
                  FECHA_VINCULACION, FUNCIONARIO_ID, CREADO_POR)
                VALUES ('CTR-00000000', ?, ?, ?, ?, ?, ?, 'it.facturacion')""",
                usuarioId, predioId, tipo, medidorId, Date.valueOf(vinculacion), funcionario("asesor"));
    }

    /** Registra la lectura del periodo (día 28) y devuelve el estado que le dio la crítica. */
    protected String registrarLectura(Long medidorId, YearMonth mes, BigDecimal lecturaActual) {
        Long id = insertar("""
                INSERT INTO SIGCA.LECTURA (MEDIDOR_ID, PERIODO, FECHA_LECTURA, LECTURA_ANTERIOR, LECTURA_ACTUAL, ORIGEN, REGISTRADA_POR)
                VALUES (?, ?, ?, 0, ?, 'MANUAL', ?)""",
                medidorId, Periodos.dePeriodo(mes), Date.valueOf(mes.atDay(28)), lecturaActual, funcionario("asesor"));
        return jdbc.queryForObject("SELECT ESTADO FROM SIGCA.LECTURA WHERE ID = ?", String.class, id);
    }

    /** Titular estrato 2 con acueducto y alcantarillado, y lectura confirmada del periodo de prueba (37 m³). */
    protected Escenario escenarioFacturable() {
        Long usuario = crearUsuario();
        Long predio = crearPredio(2);
        LocalDate inicio = mesPrueba().atDay(1);
        Long medidor = instalarMedidor(predio, "ACUEDUCTO", inicio);
        crearContrato(usuario, predio, "ACUEDUCTO", medidor, inicio);
        crearContrato(usuario, predio, "ALCANTARILLADO", null, inicio);
        String estado = registrarLectura(medidor, mesPrueba(), new BigDecimal("37"));
        if (!"CONFIRMADA".equals(estado)) {
            throw new IllegalStateException("La primera lectura debía quedar CONFIRMADA y quedó " + estado);
        }
        return new Escenario(usuario, predio, medidor);
    }

    /** Titular cuya lectura del periodo de prueba se desvía del promedio y queda EN_REVISION (P-07). */
    protected Escenario escenarioEnRevision() {
        Long usuario = crearUsuario();
        Long predio = crearPredio(3);
        YearMonth m0 = mesPrueba().minusMonths(2);
        Long medidor = instalarMedidor(predio, "ACUEDUCTO", m0.atDay(1));
        crearContrato(usuario, predio, "ACUEDUCTO", medidor, m0.atDay(1));
        registrarLectura(medidor, m0, new BigDecimal("20"));
        registrarLectura(medidor, m0.plusMonths(1), new BigDecimal("40"));
        String estado = registrarLectura(medidor, mesPrueba(), new BigDecimal("240"));
        if (!"EN_REVISION".equals(estado)) {
            throw new IllegalStateException("La lectura debía quedar EN_REVISION y quedó " + estado);
        }
        return new Escenario(usuario, predio, medidor);
    }

    /** Emite la factura del escenario sin lote (como una reexpedición o una prueba puntual) y devuelve su id. */
    protected Long emitirFactura(Escenario e) {
        TransactionTemplate tx = new TransactionTemplate(tm);
        return tx.execute(s -> emisor.emitir(new ClaveFactura(e.usuarioId(), e.predioId()), periodoPrueba(), null, null).getId());
    }

    protected String numeroFactura(Long facturaId) {
        return jdbc.queryForObject("SELECT NUMERO_FACTURA FROM SIGCA.FACTURA WHERE ID = ?", String.class, facturaId);
    }

    protected String estadoFactura(Long facturaId) {
        return jdbc.queryForObject("SELECT ESTADO FROM SIGCA.FACTURA WHERE ID = ?", String.class, facturaId);
    }

    protected BigDecimal saldo(Long facturaId) {
        return jdbc.queryForObject("SELECT SALDO_PENDIENTE FROM SIGCA.FACTURA WHERE ID = ?", BigDecimal.class, facturaId);
    }
}
