package co.gov.epq.sigca.pqr;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;

/**
 * Datos de prueba de PQR y cartera creados por SQL (como 05_pruebas.sql): titular, predio residencial, medidor y
 * contrato de acueducto, lecturas consecutivas y facturas con su línea liquidada según la tarifa vigente.
 * Todo con valores únicos: la BD es compartida y no se borra nada.
 */
public final class DatosAtencion {

    public record Titular(long usuarioId, String codigo, String documento, long predioId, long medidorId,
            long contratoId, String numeroContrato, long municipioId) {
    }

    public record FacturaCreada(long id, String numero, long lineaId, int periodo) {
    }

    private final JdbcTemplate jdbc;
    private final CifradorIdentificacion cifrador;

    public DatosAtencion(JdbcTemplate jdbc, CifradorIdentificacion cifrador) {
        this.jdbc = jdbc;
        this.cifrador = cifrador;
    }

    /** Los periodos facturables de la prueba: los tres meses anteriores al actual, del más antiguo al más reciente. */
    public static List<Integer> periodos() {
        int p3 = Periodos.anterior();
        int p2 = Periodos.dePeriodo(Periodos.aMes(p3).minusMonths(1));
        int p1 = Periodos.dePeriodo(Periodos.aMes(p3).minusMonths(2));
        return List.of(p1, p2, p3);
    }

    public long funcionario(String username) {
        return jdbc.queryForObject("SELECT ID FROM SIGCA.FUNCIONARIO WHERE USERNAME = ?", Long.class, username);
    }

    public long municipio(String dane) {
        return jdbc.queryForObject("SELECT ID FROM SIGCA.MUNICIPIO WHERE CODIGO_DANE = ?", Long.class, dane);
    }

    /** Titular persona natural (CC) con un predio residencial estrato 2 y contrato de acueducto con medidor. */
    public Titular titular(String dane, String uso, Integer estrato) {
        String doc = "7" + String.format("%08d", Math.abs(System.nanoTime()) % 100_000_000L);
        long mun = municipio(dane);
        long asesor = funcionario("asesor");
        jdbc.update("""
                INSERT INTO SIGCA.USUARIO (TIPO_PERSONA, TIPO_IDENTIFICACION, NUMERO_IDENTIFICACION_CIFRADO,
                       NUMERO_IDENTIFICACION_HASH, NUMERO_IDENTIFICACION_MASCARA, NOMBRE_COMPLETO, CORREO, TELEFONO,
                       DIRECCION_NOTIFICACION, MUNICIPIO_ID, AUTORIZA_TRATAMIENTO_DATOS, FECHA_AUTORIZACION_DATOS)
                VALUES ('NATURAL', 'CC', ?, ?, ?, ?, ?, '3101234567', 'Calle 5 # 10-20', ?, 'S', SYSTIMESTAMP)""",
                cifrador.cifrar(doc), cifrador.hash(TipoIdentificacion.CC, doc), CifradorIdentificacion.mascara(doc),
                "Titular Prueba Atencion", "titular" + doc + "@correo.co", mun);
        Long usuario = jdbc.queryForObject("SELECT ID FROM SIGCA.USUARIO WHERE NUMERO_IDENTIFICACION_HASH = ? AND TIPO_IDENTIFICACION = 'CC'",
                Long.class, cifrador.hash(TipoIdentificacion.CC, doc));
        String codigo = jdbc.queryForObject("SELECT CODIGO FROM SIGCA.USUARIO WHERE ID = ?", String.class, usuario);
        jdbc.update("INSERT INTO SIGCA.PREDIO (DIRECCION, MUNICIPIO_ID, USO, ESTRATO) VALUES (?, ?, ?, ?)",
                "Calle " + doc.substring(5) + " # 1-23", mun, uso, estrato);
        Long predio = jdbc.queryForObject("SELECT MAX(ID) FROM SIGCA.PREDIO WHERE DIRECCION = ? AND MUNICIPIO_ID = ?", Long.class,
                "Calle " + doc.substring(5) + " # 1-23", mun);
        int primero = periodos().getFirst();
        jdbc.update("""
                INSERT INTO SIGCA.MEDIDOR (SERIAL, PREDIO_ID, TIPO_SERVICIO, FECHA_INSTALACION, LECTURA_INICIAL)
                VALUES (?, ?, 'ACUEDUCTO', SIGCA.PKG_SIGCA.inicio_periodo(?), 100)""", "ACU-AT" + doc, predio, primero);
        Long medidor = jdbc.queryForObject("SELECT ID FROM SIGCA.MEDIDOR WHERE SERIAL = ?", Long.class, "ACU-AT" + doc);
        jdbc.update("""
                INSERT INTO SIGCA.CONTRATO_SERVICIO (USUARIO_ID, PREDIO_ID, TIPO_SERVICIO, MEDIDOR_ID, FECHA_VINCULACION, FUNCIONARIO_ID)
                VALUES (?, ?, 'ACUEDUCTO', ?, SIGCA.PKG_SIGCA.inicio_periodo(?), ?)""", usuario, predio, medidor, primero, asesor);
        var c = jdbc.queryForMap("SELECT ID, NUMERO_CONTRATO FROM SIGCA.CONTRATO_SERVICIO WHERE MEDIDOR_ID = ?", medidor);
        return new Titular(usuario, codigo, doc, predio, medidor, ((Number) c.get("ID")).longValue(),
                (String) c.get("NUMERO_CONTRATO"), mun);
    }

    public Titular titular(String dane) {
        return titular(dane, "RESIDENCIAL", 2);
    }

    /**
     * Lectura del periodo (consumo 20 m3) y factura con la línea de acueducto liquidada como el backend (misma lógica
     * que la función {@code linea} de 05_pruebas.sql), en un único bloque PL/SQL = una transacción (CK_FACTURA_COMPLETA).
     */
    public FacturaCreada factura(Titular t, int periodo, int lecturaActual) {
        long asesor = funcionario("asesor");
        String bloque = """
                DECLARE
                  v_lec NUMBER; v_f NUMBER; v_em DATE := SIGCA.PKG_SIGCA.fin_periodo(?) + 1;
                  v_tar SIGCA.TARIFA%ROWTYPE; v_fsu NUMBER; v_pct NUMBER := 0; v_cons NUMBER;
                  v_b NUMBER; v_c NUMBER; v_s NUMBER; v_valor NUMBER; v_sub NUMBER := 0; v_con NUMBER := 0;
                  v_mun NUMBER; v_uso VARCHAR2(20); v_estrato NUMBER; v_fin DATE; v_l NUMBER;
                BEGIN
                  INSERT INTO SIGCA.LECTURA (MEDIDOR_ID, PERIODO, FECHA_LECTURA, LECTURA_ANTERIOR, LECTURA_ACTUAL, REGISTRADA_POR)
                  VALUES (?, ?, SIGCA.PKG_SIGCA.fin_periodo(?), 0, ?, ?) RETURNING ID INTO v_lec;
                  INSERT INTO SIGCA.FACTURA (USUARIO_ID, PREDIO_ID, PERIODO, FECHA_EMISION, FECHA_VENCIMIENTO)
                  VALUES (?, ?, ?, v_em, v_em + 1) RETURNING ID INTO v_f;
                  SELECT MUNICIPIO_ID, USO, ESTRATO INTO v_mun, v_uso, v_estrato FROM SIGCA.PREDIO WHERE ID = ?;
                  v_fin := SIGCA.PKG_SIGCA.fin_periodo(?);
                  SELECT * INTO v_tar FROM SIGCA.TARIFA
                   WHERE TIPO_SERVICIO = 'ACUEDUCTO' AND MUNICIPIO_ID = v_mun AND VIGENTE_DESDE <= v_fin
                     AND (VIGENTE_HASTA IS NULL OR VIGENTE_HASTA >= v_fin);
                  SELECT CONSUMO INTO v_cons FROM SIGCA.LECTURA WHERE ID = v_lec;
                  SELECT ID, PORCENTAJE INTO v_fsu, v_pct FROM SIGCA.FACTOR_SUBSIDIO
                   WHERE TIPO_SERVICIO = 'ACUEDUCTO' AND MUNICIPIO_ID = v_mun AND USO = v_uso AND NVL(ESTRATO, 0) = NVL(v_estrato, 0)
                     AND VIGENTE_DESDE <= v_fin AND (VIGENTE_HASTA IS NULL OR VIGENTE_HASTA >= v_fin);
                  v_b := LEAST(v_cons, v_tar.CONSUMO_BASICO_M3);
                  v_s := GREATEST(v_cons - v_tar.CONSUMO_SUNTUARIO_DESDE_M3, 0);
                  v_c := v_cons - v_b - v_s;
                  v_valor := ROUND(v_b * v_tar.VALOR_M3_BASICO + v_c * NVL(v_tar.VALOR_M3_COMPLEMENTARIO, 0)
                                   + v_s * NVL(v_tar.VALOR_M3_SUNTUARIO, 0), 2);
                  IF v_pct < 0 THEN
                    v_sub := ROUND(v_b * v_tar.VALOR_M3_BASICO * v_pct / 100, 2);
                  ELSIF v_pct > 0 THEN
                    v_con := ROUND((v_tar.CARGO_FIJO + v_valor) * v_pct / 100, 2);
                  END IF;
                  INSERT INTO SIGCA.LINEA_FACTURA (FACTURA_ID, CONTRATO_ID, TIPO_SERVICIO, TARIFA_ID, FACTOR_SUBSIDIO_ID, LECTURA_ID,
                         CONSUMO, CONSUMO_BASICO, CONSUMO_COMPLEMENTARIO, CONSUMO_SUNTUARIO, CARGO_FIJO, VALOR_CONSUMO,
                         SUBSIDIO, CONTRIBUCION, TOTAL_LINEA)
                  VALUES (v_f, ?, 'ACUEDUCTO', v_tar.ID, v_fsu, v_lec, v_cons, v_b, v_c, v_s, v_tar.CARGO_FIJO, v_valor,
                          v_sub, v_con, v_tar.CARGO_FIJO + v_valor + v_sub + v_con) RETURNING ID INTO v_l;
                  ? := v_f;
                  ? := v_l;
                END;""";
        long[] ids = jdbc.execute((ConnectionCallback<long[]>) con -> {
            try (CallableStatement cs = con.prepareCall(bloque)) {
                int i = 1;
                cs.setInt(i++, periodo);
                cs.setLong(i++, t.medidorId());
                cs.setInt(i++, periodo);
                cs.setInt(i++, periodo);
                cs.setInt(i++, lecturaActual);
                cs.setLong(i++, asesor);
                cs.setLong(i++, t.usuarioId());
                cs.setLong(i++, t.predioId());
                cs.setInt(i++, periodo);
                cs.setLong(i++, t.predioId());
                cs.setInt(i++, periodo);
                cs.setLong(i++, t.contratoId());
                cs.registerOutParameter(i++, Types.NUMERIC);
                cs.registerOutParameter(i, Types.NUMERIC);
                cs.execute();
                return new long[] { cs.getLong(i - 1), cs.getLong(i) };
            }
        });
        String numero = jdbc.queryForObject("SELECT NUMERO_FACTURA FROM SIGCA.FACTURA WHERE ID = ?", String.class, ids[0]);
        return new FacturaCreada(ids[0], numero, ids[1], periodo);
    }

    /** Facturas de los tres periodos de prueba con consumo constante de 20 m3 (lecturas 120, 140, 160). */
    public List<FacturaCreada> tresFacturas(Titular t) {
        List<Integer> p = periodos();
        return List.of(factura(t, p.get(0), 120), factura(t, p.get(1), 140), factura(t, p.get(2), 160));
    }

    public void vencer(long facturaId) {
        jdbc.update("UPDATE SIGCA.FACTURA SET ESTADO = 'VENCIDA' WHERE ID = ?", facturaId);
    }

    public String estado(String tabla, long id) {
        return jdbc.queryForObject("SELECT ESTADO FROM SIGCA." + tabla + " WHERE ID = ?", String.class, id);
    }
}
