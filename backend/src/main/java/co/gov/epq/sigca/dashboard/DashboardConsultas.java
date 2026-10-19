package co.gov.epq.sigca.dashboard;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Agregados del tablero: una consulta por indicador, agrupada por municipio (sin recorrer filas en Java).
 * Prefijo SIGCA. porque la aplicación se conecta como SIGCA_APP.
 */
@Repository
public class DashboardConsultas {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Acumulador mutable por municipio. */
    static final class Acum {
        BigDecimal facturado = BigDecimal.ZERO;
        int facturas;
        BigDecimal recaudado = BigDecimal.ZERO;
        BigDecimal recaudoMes = BigDecimal.ZERO;
        BigDecimal c30 = BigDecimal.ZERO;
        BigDecimal c60 = BigDecimal.ZERO;
        BigDecimal c90 = BigDecimal.ZERO;
        BigDecimal cMas = BigDecimal.ZERO;
        int usuariosMora;
        int acuerdos;
        int pqrAbiertas;
        int pqrVencidas;
        final Map<String, Integer> porTipo = new HashMap<>();
        int respondidas;
        BigDecimal diasRespuesta = BigDecimal.ZERO;
        int extemporaneas;
        int silencios;
        int activos;
        int suspendidos;
        int lecturas;
    }

    record MunicipioFila(Long id, String nombre) {
    }

    List<MunicipioFila> municipios(Long municipioId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT ID, NOMBRE FROM SIGCA.MUNICIPIO WHERE ACTIVO = 'S'";
        if (municipioId != null) {
            sql += " AND ID = :m";
            p.addValue("m", municipioId);
        }
        return jdbc.query(sql + " ORDER BY NOMBRE", p, (rs, i) -> new MunicipioFila(rs.getLong(1), rs.getString(2)));
    }

    /** Llena los acumuladores de los municipios presentes en {@code m}. */
    void cargar(Map<Long, Acum> m, int periodo) {
        MapSqlParameterSource p = new MapSqlParameterSource("p", periodo);
        porMunicipio("""
                SELECT pr.MUNICIPIO_ID, NVL(SUM(f.TOTAL_A_PAGAR + f.VALOR_AJUSTES), 0), COUNT(*), NVL(SUM(f.VALOR_PAGADO), 0)
                  FROM SIGCA.FACTURA f JOIN SIGCA.PREDIO pr ON pr.ID = f.PREDIO_ID
                 WHERE f.PERIODO = :p AND f.ESTADO <> 'ANULADA'
                 GROUP BY pr.MUNICIPIO_ID""", p, m, (a, rs) -> {
            a.facturado = rs.getBigDecimal(2);
            a.facturas = rs.getInt(3);
            a.recaudado = rs.getBigDecimal(4);
        });
        porMunicipio("""
                SELECT NVL(pr.MUNICIPIO_ID, u.MUNICIPIO_ID), SUM(pg.VALOR)
                  FROM SIGCA.PAGO pg JOIN SIGCA.USUARIO u ON u.ID = pg.USUARIO_ID
                  LEFT JOIN SIGCA.FACTURA f ON f.ID = pg.FACTURA_ID LEFT JOIN SIGCA.PREDIO pr ON pr.ID = f.PREDIO_ID
                 WHERE pg.FECHA_PAGO >= TRUNC(SYSDATE, 'MM')
                 GROUP BY NVL(pr.MUNICIPIO_ID, u.MUNICIPIO_ID)""", p, m, (a, rs) -> a.recaudoMes = rs.getBigDecimal(2));
        porMunicipio("""
                SELECT pr.MUNICIPIO_ID,
                       NVL(SUM(CASE WHEN TRUNC(SYSDATE) - f.FECHA_VENCIMIENTO <= 30 THEN f.SALDO_PENDIENTE END), 0),
                       NVL(SUM(CASE WHEN TRUNC(SYSDATE) - f.FECHA_VENCIMIENTO BETWEEN 31 AND 60 THEN f.SALDO_PENDIENTE END), 0),
                       NVL(SUM(CASE WHEN TRUNC(SYSDATE) - f.FECHA_VENCIMIENTO BETWEEN 61 AND 90 THEN f.SALDO_PENDIENTE END), 0),
                       NVL(SUM(CASE WHEN TRUNC(SYSDATE) - f.FECHA_VENCIMIENTO > 90 THEN f.SALDO_PENDIENTE END), 0),
                       COUNT(DISTINCT f.USUARIO_ID)
                  FROM SIGCA.FACTURA f JOIN SIGCA.PREDIO pr ON pr.ID = f.PREDIO_ID
                 WHERE f.ESTADO = 'VENCIDA'
                 GROUP BY pr.MUNICIPIO_ID""", p, m, (a, rs) -> {
            a.c30 = rs.getBigDecimal(2);
            a.c60 = rs.getBigDecimal(3);
            a.c90 = rs.getBigDecimal(4);
            a.cMas = rs.getBigDecimal(5);
            a.usuariosMora = rs.getInt(6);
        });
        porMunicipio("""
                SELECT u.MUNICIPIO_ID, COUNT(*) FROM SIGCA.ACUERDO_PAGO a JOIN SIGCA.USUARIO u ON u.ID = a.USUARIO_ID
                 WHERE a.ESTADO = 'VIGENTE' GROUP BY u.MUNICIPIO_ID""", p, m, (a, rs) -> a.acuerdos = rs.getInt(2));
        porMunicipio("""
                SELECT MUNICIPIO_ID, TIPO, ESTADO, COUNT(*) FROM SIGCA.PQR
                 WHERE ESTADO IN ('RECIBIDA','EN_TRAMITE','VENCIDA')
                 GROUP BY MUNICIPIO_ID, TIPO, ESTADO""", p, m, (a, rs) -> {
            int n = rs.getInt(4);
            a.pqrAbiertas += n;
            if ("VENCIDA".equals(rs.getString(3))) {
                a.pqrVencidas += n;
            }
            a.porTipo.merge(rs.getString(2), n, Integer::sum);
        });
        porMunicipio("""
                SELECT MUNICIPIO_ID, COUNT(*),
                       NVL(SUM(CAST(FECHA_RESPUESTA AS DATE) - CAST(FECHA_REGISTRO AS DATE)), 0),
                       SUM(CASE WHEN RESPUESTA_EXTEMPORANEA = 'S' THEN 1 ELSE 0 END)
                  FROM SIGCA.PQR
                 WHERE FECHA_RESPUESTA >= ADD_MONTHS(TRUNC(SYSDATE), -12)
                 GROUP BY MUNICIPIO_ID""", p, m, (a, rs) -> {
            a.respondidas = rs.getInt(2);
            a.diasRespuesta = rs.getBigDecimal(3);
            a.extemporaneas = rs.getInt(4);
        });
        porMunicipio("""
                SELECT MUNICIPIO_ID, COUNT(*) FROM SIGCA.PQR
                 WHERE SILENCIO_POSITIVO = 'S' AND FECHA_RECONOCIMIENTO_SILENCIO IS NULL
                 GROUP BY MUNICIPIO_ID""", p, m, (a, rs) -> a.silencios = rs.getInt(2));
        porMunicipio("""
                SELECT pr.MUNICIPIO_ID, SUM(CASE WHEN c.ESTADO = 'ACTIVO' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN c.ESTADO = 'SUSPENDIDO' THEN 1 ELSE 0 END)
                  FROM SIGCA.CONTRATO_SERVICIO c JOIN SIGCA.PREDIO pr ON pr.ID = c.PREDIO_ID
                 WHERE c.ESTADO IN ('ACTIVO','SUSPENDIDO')
                 GROUP BY pr.MUNICIPIO_ID""", p, m, (a, rs) -> {
            a.activos = rs.getInt(2);
            a.suspendidos = rs.getInt(3);
        });
        porMunicipio("""
                SELECT pr.MUNICIPIO_ID, COUNT(*)
                  FROM SIGCA.LECTURA l JOIN SIGCA.MEDIDOR me ON me.ID = l.MEDIDOR_ID JOIN SIGCA.PREDIO pr ON pr.ID = me.PREDIO_ID
                 WHERE l.ESTADO = 'EN_REVISION'
                 GROUP BY pr.MUNICIPIO_ID""", p, m, (a, rs) -> a.lecturas = rs.getInt(2));
    }

    List<DashboardVistas.PuntoTendencia> tendencia(int desde, int hasta, Long municipioId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("d", desde).addValue("h", hasta);
        String filtro = "";
        if (municipioId != null) {
            filtro = " AND pr.MUNICIPIO_ID = :m";
            p.addValue("m", municipioId);
        }
        return jdbc.query("""
                SELECT f.PERIODO, NVL(SUM(f.TOTAL_A_PAGAR + f.VALOR_AJUSTES), 0), NVL(SUM(f.VALOR_PAGADO), 0)
                  FROM SIGCA.FACTURA f JOIN SIGCA.PREDIO pr ON pr.ID = f.PREDIO_ID
                 WHERE f.PERIODO BETWEEN :d AND :h AND f.ESTADO <> 'ANULADA'""" + filtro + " GROUP BY f.PERIODO ORDER BY f.PERIODO",
                p, (rs, i) -> new DashboardVistas.PuntoTendencia(rs.getInt(1), rs.getBigDecimal(2), rs.getBigDecimal(3)));
    }

    @FunctionalInterface
    private interface Fila {
        void aplicar(Acum a, ResultSet rs) throws SQLException;
    }

    private void porMunicipio(String sql, MapSqlParameterSource p, Map<Long, Acum> m, Fila fila) {
        jdbc.query(sql, p, (RowCallbackHandler) rs -> {
            Acum a = m.get(rs.getLong(1));
            if (a != null) {
                fila.aplicar(a, rs);
            }
        });
    }
}
