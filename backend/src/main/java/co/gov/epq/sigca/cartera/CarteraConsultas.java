package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de cartera en SQL nativo (agregados sobre FACTURA, COBRO_PERSUASIVO, ACUERDO_PAGO, ORDEN_SERVICIO).
 * Se usa el prefijo SIGCA. porque la aplicación se conecta como SIGCA_APP.
 */
@Repository
public class CarteraConsultas {

    private final NamedParameterJdbcTemplate jdbc;

    public CarteraConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Filtros de morosos. antiguedad: 30, 60, 90 o MAS_90 (días desde el primer vencimiento). cobro: SIN_COBRO, ACTIVO, CANDIDATO, AVISADO, EN_ACUERDO. */
    public record FiltroMorosos(Long municipioId, String antiguedad, String cobro, String texto) {
    }

    record UsuarioCartera(Long id, String codigo, String nombre, String correo, String telefono, boolean activo) {
    }

    record DeudaVencida(int facturas, int periodos, BigDecimal saldo, BigDecimal reclamo) {
        BigDecimal exigible() {
            return saldo.subtract(reclamo);
        }
    }

    record ContratoMora(Long id, String numero, Long municipioId, boolean conMora) {
    }

    private static final String MOROSOS = """
            WITH d AS (
              SELECT f.USUARIO_ID, COUNT(*) FACTURAS, COUNT(DISTINCT f.PERIODO) PERIODOS,
                     SUM(f.SALDO_PENDIENTE) SALDO, SUM(f.VALOR_EN_RECLAMO) RECLAMO, MIN(f.FECHA_VENCIMIENTO) PRIMER_VENC,
                     LISTAGG(DISTINCT m.NOMBRE, ', ') WITHIN GROUP (ORDER BY m.NOMBRE) MUNICIPIOS
                FROM SIGCA.FACTURA f
                JOIN SIGCA.PREDIO p ON p.ID = f.PREDIO_ID
                JOIN SIGCA.MUNICIPIO m ON m.ID = p.MUNICIPIO_ID
               WHERE f.ESTADO = 'VENCIDA' %s
               GROUP BY f.USUARIO_ID)
            SELECT u.ID, u.CODIGO, u.NOMBRE_COMPLETO, d.MUNICIPIOS, d.FACTURAS, d.PERIODOS, d.SALDO, d.RECLAMO,
                   d.PRIMER_VENC, TRUNC(SYSDATE) - d.PRIMER_VENC DIAS,
                   c.ID COBRO_ID, c.ESTADO COBRO_ESTADO, c.CANDIDATO_SUSPENSION, c.FECHA_AVISO_SUSPENSION, c.FECHA_INICIO,
                   (SELECT COUNT(*) FROM SIGCA.ACUERDO_PAGO a WHERE a.USUARIO_ID = u.ID AND a.ESTADO = 'VIGENTE') ACUERDOS,
                   (SELECT COUNT(*) FROM SIGCA.ORDEN_SERVICIO o JOIN SIGCA.CONTRATO_SERVICIO cs ON cs.ID = o.CONTRATO_ID
                     WHERE cs.USUARIO_ID = u.ID AND o.ESTADO = 'PENDIENTE' AND o.TIPO = 'SUSPENSION') ORDENES,
                   (SELECT COUNT(*) FROM SIGCA.CONTRATO_SERVICIO cs WHERE cs.USUARIO_ID = u.ID AND cs.ESTADO = 'ACTIVO') ACTIVOS
              FROM d
              JOIN SIGCA.USUARIO u ON u.ID = d.USUARIO_ID
              LEFT JOIN SIGCA.COBRO_PERSUASIVO c ON c.USUARIO_ID = u.ID AND c.ESTADO IN ('ACTIVO','SUSPENDIDO')
             WHERE 1 = 1 %s
            """;

    private static final Map<String, String> ORDEN = Map.of(
            "deuda", "d.SALDO - d.RECLAMO", "dias", "d.PRIMER_VENC", "periodos", "d.PERIODOS", "nombre", "u.NOMBRE_COMPLETO");

    public long contarMorosos(FiltroMorosos f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM (" + morosos(f, p) + ")";
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    /** @param orden clave de {@link #ORDEN} (deuda por defecto), {@code asc} el sentido. */
    public List<CarteraVistas.Moroso> morosos(FiltroMorosos f, String orden, boolean asc, long offset, int limite,
            int periodosSuspension) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String columna = ORDEN.getOrDefault(orden == null ? "deuda" : orden, ORDEN.get("deuda"));
        // Para "dias" el orden natural (más días primero) es la fecha de vencimiento más antigua.
        boolean ascendente = "d.PRIMER_VENC".equals(columna) ? !asc : asc;
        String sql = morosos(f, p) + " ORDER BY " + columna + (ascendente ? " ASC" : " DESC") + ", u.ID"
                + " OFFSET :off ROWS FETCH NEXT :lim ROWS ONLY";
        p.addValue("off", offset).addValue("lim", limite);
        return jdbc.query(sql, p, (rs, i) -> moroso(rs, periodosSuspension));
    }

    private static String morosos(FiltroMorosos f, MapSqlParameterSource p) {
        StringBuilder facturas = new StringBuilder();
        StringBuilder externo = new StringBuilder();
        if (f.municipioId() != null) {
            facturas.append(" AND p.MUNICIPIO_ID = :mun");
            p.addValue("mun", f.municipioId());
        }
        if (f.antiguedad() != null) {
            externo.append(switch (f.antiguedad()) {
                case "30" -> " AND TRUNC(SYSDATE) - d.PRIMER_VENC <= 30";
                case "60" -> " AND TRUNC(SYSDATE) - d.PRIMER_VENC BETWEEN 31 AND 60";
                case "90" -> " AND TRUNC(SYSDATE) - d.PRIMER_VENC BETWEEN 61 AND 90";
                case "MAS_90" -> " AND TRUNC(SYSDATE) - d.PRIMER_VENC > 90";
                default -> "";
            });
        }
        if (f.cobro() != null) {
            externo.append(switch (f.cobro()) {
                case "SIN_COBRO" -> " AND c.ID IS NULL";
                case "ACTIVO" -> " AND c.ESTADO = 'ACTIVO' AND c.CANDIDATO_SUSPENSION = 'N'";
                case "CANDIDATO" -> " AND c.ESTADO = 'ACTIVO' AND c.CANDIDATO_SUSPENSION = 'S' AND c.FECHA_AVISO_SUSPENSION IS NULL";
                case "AVISADO" -> " AND c.ESTADO = 'ACTIVO' AND c.FECHA_AVISO_SUSPENSION IS NOT NULL";
                case "EN_ACUERDO" -> " AND c.ESTADO = 'SUSPENDIDO'";
                default -> "";
            });
        }
        if (f.texto() != null && !f.texto().isBlank()) {
            externo.append(" AND (UPPER(u.NOMBRE_COMPLETO) LIKE :txt ESCAPE '\\' OR u.CODIGO = :cod)");
            String t = f.texto().strip().toUpperCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            p.addValue("txt", "%" + t + "%").addValue("cod", f.texto().strip().toUpperCase());
        }
        return MOROSOS.formatted(facturas, externo);
    }

    private static CarteraVistas.Moroso moroso(ResultSet rs, int periodosSuspension) throws SQLException {
        BigDecimal saldo = rs.getBigDecimal("SALDO");
        BigDecimal reclamo = rs.getBigDecimal("RECLAMO");
        long cobroId = rs.getLong("COBRO_ID");
        boolean hayCobro = !rs.wasNull();
        String estado = rs.getString("COBRO_ESTADO");
        int periodos = rs.getInt("PERIODOS");
        return new CarteraVistas.Moroso(rs.getLong("ID"), rs.getString("CODIGO"), rs.getString("NOMBRE_COMPLETO"),
                rs.getString("MUNICIPIOS"), rs.getInt("FACTURAS"), periodos, saldo, reclamo, saldo.subtract(reclamo),
                fecha(rs, "PRIMER_VENC"), rs.getInt("DIAS"), hayCobro ? cobroId : null,
                estado == null ? null : EstadoCobro.valueOf(estado), "S".equals(rs.getString("CANDIDATO_SUSPENSION")),
                fechaHora(rs, "FECHA_AVISO_SUSPENSION"), fecha(rs, "FECHA_INICIO"), rs.getInt("ACUERDOS") > 0,
                rs.getInt("ORDENES"), rs.getInt("ACTIVOS"), periodos >= periodosSuspension);
    }

    // ------------------------------------------------------------------ usuario

    Optional<UsuarioCartera> usuario(Long id) {
        return jdbc.query("""
                SELECT ID, CODIGO, NOMBRE_COMPLETO, CORREO, TELEFONO, ACTIVO FROM SIGCA.USUARIO WHERE ID = :id""",
                Map.of("id", id), (rs, i) -> new UsuarioCartera(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), "S".equals(rs.getString(6))))
                .stream().findFirst();
    }

    /** Municipios de los predios del usuario (contratos y facturas), para restringir a funcionarios con municipio. */
    List<Long> municipiosDeUsuario(Long usuarioId) {
        return jdbc.queryForList("""
                SELECT DISTINCT p.MUNICIPIO_ID FROM SIGCA.PREDIO p
                 WHERE p.ID IN (SELECT PREDIO_ID FROM SIGCA.CONTRATO_SERVICIO WHERE USUARIO_ID = :u
                                UNION SELECT PREDIO_ID FROM SIGCA.FACTURA WHERE USUARIO_ID = :u)""",
                Map.of("u", usuarioId), Long.class);
    }

    DeudaVencida deudaVencida(Long usuarioId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*), COUNT(DISTINCT PERIODO), NVL(SUM(SALDO_PENDIENTE), 0), NVL(SUM(VALOR_EN_RECLAMO), 0)
                  FROM SIGCA.FACTURA WHERE USUARIO_ID = :u AND ESTADO = 'VENCIDA'""",
                Map.of("u", usuarioId), (rs, i) -> new DeudaVencida(rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4)));
    }

    List<CarteraVistas.FacturaVencida> facturasVencidas(Long usuarioId) {
        return jdbc.query("""
                SELECT f.ID, f.NUMERO_FACTURA, f.PERIODO, f.FECHA_VENCIMIENTO, f.TOTAL_A_PAGAR, f.SALDO_PENDIENTE,
                       f.VALOR_EN_RECLAMO, p.USO, p.DIRECCION, m.NOMBRE
                  FROM SIGCA.FACTURA f JOIN SIGCA.PREDIO p ON p.ID = f.PREDIO_ID JOIN SIGCA.MUNICIPIO m ON m.ID = p.MUNICIPIO_ID
                 WHERE f.USUARIO_ID = :u AND f.ESTADO = 'VENCIDA'
                 ORDER BY f.PERIODO, f.ID""", Map.of("u", usuarioId),
                (rs, i) -> new CarteraVistas.FacturaVencida(rs.getLong(1), rs.getString(2), rs.getInt(3), fecha(rs, 4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getBigDecimal(7).signum() == 0 && rs.getBigDecimal(6).signum() > 0));
    }

    Optional<String> acuerdoVigente(Long usuarioId) {
        return jdbc.queryForList("SELECT NUMERO_ACUERDO FROM SIGCA.ACUERDO_PAGO WHERE USUARIO_ID = :u AND ESTADO = 'VIGENTE'",
                Map.of("u", usuarioId), String.class).stream().findFirst();
    }

    /** Contratos ACTIVOS del usuario; conMora si su predio tiene facturas VENCIDAS del usuario. */
    List<ContratoMora> contratosActivos(Long usuarioId) {
        return jdbc.query("""
                SELECT c.ID, c.NUMERO_CONTRATO, p.MUNICIPIO_ID,
                       (SELECT COUNT(*) FROM SIGCA.FACTURA f WHERE f.USUARIO_ID = c.USUARIO_ID AND f.PREDIO_ID = c.PREDIO_ID
                           AND f.ESTADO = 'VENCIDA' AND f.SALDO_PENDIENTE - f.VALOR_EN_RECLAMO > 0) MORA
                  FROM SIGCA.CONTRATO_SERVICIO c JOIN SIGCA.PREDIO p ON p.ID = c.PREDIO_ID
                 WHERE c.USUARIO_ID = :u AND c.ESTADO = 'ACTIVO' ORDER BY c.NUMERO_CONTRATO""",
                Map.of("u", usuarioId), (rs, i) -> new ContratoMora(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getInt(4) > 0));
    }

    /** Contratos con orden de suspensión o reconexión pendiente. */
    List<Long> contratosConOrdenPendiente(Long usuarioId) {
        return jdbc.queryForList("""
                SELECT DISTINCT o.CONTRATO_ID FROM SIGCA.ORDEN_SERVICIO o JOIN SIGCA.CONTRATO_SERVICIO c ON c.ID = o.CONTRATO_ID
                 WHERE c.USUARIO_ID = :u AND o.ESTADO = 'PENDIENTE'""", Map.of("u", usuarioId), Long.class);
    }

    List<CarteraVistas.Orden> ordenesDeCobro(Long cobroId) {
        return jdbc.query("""
                SELECT o.ID, o.NUMERO_ORDEN, o.CONTRATO_ID, c.NUMERO_CONTRATO, o.ESTADO, o.FECHA_ORDEN
                  FROM SIGCA.ORDEN_SERVICIO o JOIN SIGCA.CONTRATO_SERVICIO c ON c.ID = o.CONTRATO_ID
                 WHERE o.COBRO_ID = :c ORDER BY o.ID""", Map.of("c", cobroId),
                (rs, i) -> new CarteraVistas.Orden(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getString(5), fechaHora(rs, "FECHA_ORDEN")));
    }

    /** Usos de los predios de las facturas dadas (para el tope de tasa, RN-38). */
    List<String> usosDeFacturas(List<Long> facturaIds) {
        return jdbc.queryForList("""
                SELECT DISTINCT p.USO FROM SIGCA.FACTURA f JOIN SIGCA.PREDIO p ON p.ID = f.PREDIO_ID WHERE f.ID IN (:ids)""",
                Map.of("ids", facturaIds), String.class);
    }

    /** Funcionarios activos con el rol dado en el municipio. */
    List<Long> funcionariosConRol(String rol, Long municipioId) {
        return jdbc.queryForList("""
                SELECT DISTINCT f.ID FROM SIGCA.FUNCIONARIO f
                  JOIN SIGCA.FUNCIONARIO_ROL fr ON fr.FUNCIONARIO_ID = f.ID JOIN SIGCA.ROL r ON r.ID = fr.ROL_ID
                 WHERE f.ACTIVO = 'S' AND r.ACTIVO = 'S' AND r.CODIGO = :rol AND f.MUNICIPIO_ID = :m""",
                Map.of("rol", rol, "m", municipioId), Long.class);
    }

    // ------------------------------------------------------------------ acuerdos

    private static final String ACUERDOS = """
            SELECT a.ID, a.NUMERO_ACUERDO, a.USUARIO_ID, u.CODIGO, u.NOMBRE_COMPLETO, a.FECHA_ACUERDO, a.TOTAL_DEUDA,
                   a.CUOTA_INICIAL, a.TASA_INTERES_ANUAL, a.NUMERO_CUOTAS, a.ESTADO, a.FECHA_ESTADO, a.MOTIVO_ESTADO,
                   fu.NOMBRE_COMPLETO FUNCIONARIO,
                   (SELECT COUNT(*) FROM SIGCA.CUOTA_ACUERDO q WHERE q.ACUERDO_ID = a.ID AND q.ESTADO = 'PAGADA') PAGADAS,
                   (SELECT COUNT(*) FROM SIGCA.CUOTA_ACUERDO q WHERE q.ACUERDO_ID = a.ID AND q.ESTADO = 'VENCIDA') VENCIDAS,
                   (SELECT MIN(q.FECHA_VENCIMIENTO) FROM SIGCA.CUOTA_ACUERDO q WHERE q.ACUERDO_ID = a.ID AND q.ESTADO = 'PENDIENTE') PROXIMA,
                   (SELECT NVL(SUM(q.VALOR_CUOTA), 0) FROM SIGCA.CUOTA_ACUERDO q WHERE q.ACUERDO_ID = a.ID AND q.ESTADO <> 'PAGADA') SALDO
              FROM SIGCA.ACUERDO_PAGO a JOIN SIGCA.USUARIO u ON u.ID = a.USUARIO_ID
              JOIN SIGCA.FUNCIONARIO fu ON fu.ID = a.FUNCIONARIO_ID
            """;

    private static String filtroAcuerdos(EstadoAcuerdo estado, Long usuarioId, String texto, Long municipioId,
            MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (estado != null) {
            w.append(" AND a.ESTADO = :e");
            p.addValue("e", estado.name());
        }
        if (usuarioId != null) {
            w.append(" AND a.USUARIO_ID = :u");
            p.addValue("u", usuarioId);
        }
        if (texto != null && !texto.isBlank()) {
            w.append(" AND (a.NUMERO_ACUERDO = :t OR u.CODIGO = :t)");
            p.addValue("t", texto.strip().toUpperCase());
        }
        if (municipioId != null) {
            w.append(" AND EXISTS (SELECT 1 FROM SIGCA.ACUERDO_FACTURA af JOIN SIGCA.FACTURA f ON f.ID = af.FACTURA_ID")
                    .append(" JOIN SIGCA.PREDIO p ON p.ID = f.PREDIO_ID WHERE af.ACUERDO_ID = a.ID AND p.MUNICIPIO_ID = :m)");
            p.addValue("m", municipioId);
        }
        return w.toString();
    }

    long contarAcuerdos(EstadoAcuerdo estado, Long usuarioId, String texto, Long municipioId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM SIGCA.ACUERDO_PAGO a JOIN SIGCA.USUARIO u ON u.ID = a.USUARIO_ID"
                + filtroAcuerdos(estado, usuarioId, texto, municipioId, p);
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    List<CarteraVistas.Acuerdo> acuerdos(EstadoAcuerdo estado, Long usuarioId, String texto, Long municipioId,
            long offset, int limite) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = ACUERDOS + filtroAcuerdos(estado, usuarioId, texto, municipioId, p)
                + " ORDER BY a.ID DESC OFFSET :off ROWS FETCH NEXT :lim ROWS ONLY";
        p.addValue("off", offset).addValue("lim", limite);
        return jdbc.query(sql, p, (rs, i) -> acuerdo(rs));
    }

    Optional<CarteraVistas.Acuerdo> acuerdo(Long id) {
        return jdbc.query(ACUERDOS + " WHERE a.ID = :id", Map.of("id", id), (rs, i) -> acuerdo(rs)).stream().findFirst();
    }

    private static CarteraVistas.Acuerdo acuerdo(ResultSet rs) throws SQLException {
        return new CarteraVistas.Acuerdo(rs.getLong("ID"), rs.getString("NUMERO_ACUERDO"), rs.getLong("USUARIO_ID"),
                rs.getString("CODIGO"), rs.getString("NOMBRE_COMPLETO"), fecha(rs, "FECHA_ACUERDO"),
                rs.getBigDecimal("TOTAL_DEUDA"), rs.getBigDecimal("CUOTA_INICIAL"), rs.getBigDecimal("TASA_INTERES_ANUAL"),
                rs.getInt("NUMERO_CUOTAS"), EstadoAcuerdo.valueOf(rs.getString("ESTADO")), fechaHora(rs, "FECHA_ESTADO"),
                rs.getString("MOTIVO_ESTADO"), rs.getString("FUNCIONARIO"), rs.getInt("PAGADAS"), rs.getInt("VENCIDAS"),
                fecha(rs, "PROXIMA"), rs.getBigDecimal("SALDO"));
    }

    List<CarteraVistas.FacturaAcuerdo> facturasDeAcuerdo(Long acuerdoId) {
        return jdbc.query("""
                SELECT f.ID, f.NUMERO_FACTURA, f.PERIODO, af.VALOR_INCLUIDO, f.ESTADO
                  FROM SIGCA.ACUERDO_FACTURA af JOIN SIGCA.FACTURA f ON f.ID = af.FACTURA_ID
                 WHERE af.ACUERDO_ID = :a ORDER BY f.PERIODO, f.ID""", Map.of("a", acuerdoId),
                (rs, i) -> new CarteraVistas.FacturaAcuerdo(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getBigDecimal(4), rs.getString(5)));
    }

    // ------------------------------------------------------------------ utilidades

    static LocalDate fecha(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime().toLocalDate();
    }

    static LocalDate fecha(ResultSet rs, int col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime().toLocalDate();
    }

    static LocalDateTime fechaHora(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
