package co.gov.epq.sigca.facturacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Lote de facturación sobre predios propios de la prueba (la BD es compartida): la BD acepta la liquidación de
 * Java (TRG_LINEA_BI), un predio con lectura en revisión queda en DETALLE_ERRORES sin detener el lote, y al
 * relanzar solo se procesa lo pendiente.
 */
class LoteFacturacionIT extends PruebaFacturacionBase {

    @Autowired
    LoteFacturacionService lotes;

    @Test
    void elLoteFacturaLoPendienteYRegistraLosPrediosConError() {
        Escenario ok = escenarioFacturable();
        Escenario revision = escenarioEnRevision();
        int periodo = periodoPrueba();

        LoteVista lote = lotes.ejecutar(periodo, List.of(ok.predioId(), revision.predioId()));

        assertThat(lote.estado()).isEqualTo(EstadoLote.FALLIDO);
        assertThat(lote.totalPredios()).isEqualTo(2);
        assertThat(lote.totalFacturas()).isEqualTo(1);
        assertThat(lote.totalErrores()).isEqualTo(1);
        assertThat(lote.errores()).singleElement().satisfies(e -> {
            assertThat(e.predioId()).isEqualTo(revision.predioId());
            assertThat(e.mensaje()).contains("EN_REVISION");
        });

        // La BD aceptó las líneas liquidadas en Java: 97.000 de acueducto + 72.820 de alcantarillado (P4)
        var f = jdbc.queryForMap("""
                SELECT ID, ESTADO, NUMERO_LINEAS, TOTAL_A_PAGAR, SALDO_PENDIENTE, LOTE_ID, ESTADO_SYNC_ERP
                FROM SIGCA.FACTURA WHERE PREDIO_ID = ? AND PERIODO = ?""", ok.predioId(), periodo);
        assertThat(f.get("ESTADO")).isEqualTo("PENDIENTE");
        assertThat(((Number) f.get("NUMERO_LINEAS")).intValue()).isEqualTo(2);
        assertThat((BigDecimal) f.get("TOTAL_A_PAGAR")).isEqualByComparingTo("169820");
        assertThat((BigDecimal) f.get("SALDO_PENDIENTE")).isEqualByComparingTo("169820");
        assertThat(((Number) f.get("LOTE_ID")).longValue()).isEqualTo(lote.id());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.FACTURA WHERE PREDIO_ID = ? AND PERIODO = ?""", Integer.class,
                revision.predioId(), periodo)).isZero();

        var bd = jdbc.queryForMap("SELECT ESTADO, TOTAL_FACTURAS, TOTAL_ERRORES, DETALLE_ERRORES FROM SIGCA.LOTE_FACTURACION WHERE ID = ?",
                lote.id());
        assertThat(bd.get("ESTADO")).isEqualTo("FALLIDO");
        assertThat(String.valueOf(bd.get("DETALLE_ERRORES"))).contains(String.valueOf(revision.predioId()));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'GENERAR_LOTE_FACTURAS'
                AND ENTIDAD = 'LOTE_FACTURACION' AND ENTIDAD_ID = ?""", Integer.class, lote.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.NOTIFICACION WHERE EVENTO = 'FACTURA_EMITIDA' AND ENTIDAD = 'FACTURA' AND ENTIDAD_ID = ?""",
                Integer.class, ((Number) f.get("ID")).longValue())).isEqualTo(1);

        // Relanzar: la factura ya emitida no se repite; solo queda el predio en revisión
        LoteVista relanzado = lotes.ejecutar(periodo, List.of(ok.predioId(), revision.predioId()));
        assertThat(relanzado.totalPredios()).isEqualTo(1);
        assertThat(relanzado.totalFacturas()).isZero();
        assertThat(relanzado.estado()).isEqualTo(EstadoLote.FALLIDO);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.FACTURA WHERE PREDIO_ID = ? AND PERIODO = ?",
                Integer.class, ok.predioId(), periodo)).isEqualTo(1);
    }
}
