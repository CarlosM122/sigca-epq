package co.gov.epq.sigca.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.facturacion.PruebaFacturacionBase;

/** Pago presencial y bancario de una factura (RF-06, P-05, P-23). */
class PagoPresencialIT extends PruebaFacturacionBase {

    @Test
    void elPagoPresencialPorElValorExigibleDejaLaFacturaPagada() throws Exception {
        Long id = emitirFactura(escenarioFacturable());
        String numero = numeroFactura(id);

        mvc.perform(get("/api/pagos/obligacion").with(como("asesor")).param("referencia", numero.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("FACTURA"))
                .andExpect(jsonPath("$.valorExigible").value(169820.0))
                .andExpect(jsonPath("$.valorEnReclamo").value(0.0))
                .andExpect(jsonPath("$.admitePago").value(true));

        // No se reciben abonos (P-05)
        mvc.perform(post("/api/pagos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"referencia":"%s","canal":"PRESENCIAL","valor":100000}""".formatted(numero)))
                .andExpect(status().isUnprocessableContent());
        assertThat(estadoFactura(id)).isEqualTo("PENDIENTE");

        String r = mvc.perform(post("/api/pagos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"referencia":"%s","canal":"PRESENCIAL","valor":169820}""".formatted(numero)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoObligacion").value("PAGADA"))
                .andReturn().getResponse().getContentAsString();
        Long pagoId = ((Number) JsonPath.read(r, "$.id")).longValue();

        assertThat(estadoFactura(id)).isEqualTo("PAGADA");
        assertThat(saldo(id)).isEqualByComparingTo(BigDecimal.ZERO);
        var p = jdbc.queryForMap("SELECT CANAL_PAGO, FUNCIONARIO_ID, VALOR FROM SIGCA.PAGO WHERE ID = ?", pagoId);
        assertThat(p.get("CANAL_PAGO")).isEqualTo("PRESENCIAL");
        assertThat(((Number) p.get("FUNCIONARIO_ID")).longValue()).isEqualTo(funcionario("asesor"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'REGISTRAR_PAGO' AND ENTIDAD = 'PAGO' AND ENTIDAD_ID = ?""",
                Integer.class, pagoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.NOTIFICACION WHERE EVENTO = 'COMPROBANTE_PAGO' AND ENTIDAD_ID = ?""",
                Integer.class, pagoId)).isEqualTo(1);

        mvc.perform(get("/api/pagos/{id}/comprobante", pagoId).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // Una factura pagada no admite otro pago
        mvc.perform(post("/api/pagos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"referencia":"%s","canal":"PRESENCIAL","valor":169820}""".formatted(numero)))
                .andExpect(status().isConflict());
    }

    @Test
    void elRecaudoBancarioExigeLaReferenciaDelBanco() throws Exception {
        Long id = emitirFactura(escenarioFacturable());
        String numero = numeroFactura(id);
        mvc.perform(post("/api/pagos").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"referencia":"%s","canal":"BANCO","valor":169820}""".formatted(numero)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/pagos").with(como("cartera")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"referencia":"%s","canal":"BANCO","valor":169820,"referenciaBanco":"bco-%s"}""".formatted(numero, unico())))
                .andExpect(status().isCreated());
        assertThat(estadoFactura(id)).isEqualTo("PAGADA");
        assertThat(jdbc.queryForObject("SELECT REFERENCIA_BANCO FROM SIGCA.PAGO WHERE FACTURA_ID = ?", String.class, id))
                .startsWith("BCO-");
    }
}
