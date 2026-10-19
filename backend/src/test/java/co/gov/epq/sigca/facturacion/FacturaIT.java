package co.gov.epq.sigca.facturacion;

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

/** Consulta, PDF, anulación y reexpedición de facturas (RF-07, P-10). */
class FacturaIT extends PruebaFacturacionBase {

    private int version(Long id) throws Exception {
        String r = mvc.perform(get("/api/facturas/{id}", id).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineas.length()").value(2))
                .andExpect(jsonPath("$.valorExigible").value(169820.0))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.version");
    }

    @Test
    void anularConReexpedicionDejaTrazaYUnaNuevaFacturaConLaMismaLiquidacion() throws Exception {
        Escenario e = escenarioFacturable();
        Long id = emitirFactura(e);
        String numero = numeroFactura(id);

        mvc.perform(get("/api/facturas").with(como("asesor")).param("numero", numero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/facturas/{id}/pdf", id).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        String r = mvc.perform(post("/api/facturas/{id}/anulacion", id).with(como("asesor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"motivo":"Lectura mal digitada en terreno","reexpedir":true,"version":%d}""".formatted(version(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anulada.estado").value("ANULADA"))
                .andExpect(jsonPath("$.reexpedida.reexpedicionDe").value(numero))
                .andReturn().getResponse().getContentAsString();
        Long nueva = ((Number) JsonPath.read(r, "$.reexpedida.id")).longValue();

        var anulada = jdbc.queryForMap("SELECT ESTADO, ANULADA_POR, MOTIVO_ANULACION FROM SIGCA.FACTURA WHERE ID = ?", id);
        assertThat(anulada.get("ESTADO")).isEqualTo("ANULADA");
        assertThat(((Number) anulada.get("ANULADA_POR")).longValue()).isEqualTo(funcionario("asesor"));
        var reexp = jdbc.queryForMap("SELECT ESTADO, REEXPEDICION_DE_ID, TOTAL_A_PAGAR FROM SIGCA.FACTURA WHERE ID = ?", nueva);
        assertThat(reexp.get("ESTADO")).isEqualTo("PENDIENTE");
        assertThat(((Number) reexp.get("REEXPEDICION_DE_ID")).longValue()).isEqualTo(id);
        assertThat((BigDecimal) reexp.get("TOTAL_A_PAGAR")).isEqualByComparingTo("169820");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION IN ('ANULAR_FACTURA','REEXPEDIR_FACTURA')
                AND ENTIDAD = 'FACTURA' AND ENTIDAD_ID IN (?, ?)""", Integer.class, id, nueva)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.NOTIFICACION WHERE EVENTO = 'FACTURA_REEXPEDIDA' AND ENTIDAD_ID = ?""",
                Integer.class, nueva)).isEqualTo(1);

        // Una anulada solo se reexpide una vez
        mvc.perform(post("/api/facturas/{id}/reexpedicion", id).with(como("asesor")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void anularSinReexpedirYReexpedirDespues() throws Exception {
        Escenario e = escenarioFacturable();
        Long id = emitirFactura(e);

        mvc.perform(post("/api/facturas/{id}/anulacion", id).with(como("asesor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"motivo":"Error en el estrato del predio","reexpedir":false,"version":%d}""".formatted(version(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reexpedida").doesNotExist());
        assertThat(estadoFactura(id)).isEqualTo("ANULADA");

        mvc.perform(post("/api/facturas/{id}/reexpedicion", id).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.totalAPagar").value(169820.0));
    }

    @Test
    void sinPermisoNoSeAnulaYMotivoCortoSeRechaza() throws Exception {
        Long id = emitirFactura(escenarioFacturable());
        int v = version(id);
        mvc.perform(post("/api/facturas/{id}/anulacion", id).with(como("gerente"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"motivo":"Motivo suficientemente largo","version":%d}""".formatted(v)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/facturas/{id}/anulacion", id).with(como("asesor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"motivo":"corto","version":%d}""".formatted(v)))
                .andExpect(status().isBadRequest());
        assertThat(estadoFactura(id)).isEqualTo("PENDIENTE");
    }
}
