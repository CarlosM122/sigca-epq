package co.gov.epq.sigca.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import co.gov.epq.sigca.soporte.PruebaIntegracion;

class DashboardIT extends PruebaIntegracion {

    @Test
    void elGerenteVeIndicadoresPorMunicipioYConsolidados() throws Exception {
        mvc.perform(get("/api/dashboard").with(como("gerente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refrescoSegundos").value(60))
                .andExpect(jsonPath("$.porMunicipio.length()").value(9))
                .andExpect(jsonPath("$.global.pqrAbiertasPorTipo.RECLAMO").exists())
                .andExpect(jsonPath("$.global.carteraPorEdad.mas90").exists())
                .andExpect(jsonPath("$.tendencia").isArray());
        Long circasia = jdbc.queryForObject("SELECT ID FROM SIGCA.MUNICIPIO WHERE CODIGO_DANE = '63190'", Long.class);
        mvc.perform(get("/api/dashboard").param("municipioId", circasia.toString()).param("periodo", "202609").with(como("gerente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value(202609))
                .andExpect(jsonPath("$.porMunicipio.length()").value(1))
                .andExpect(jsonPath("$.porMunicipio[0].municipio").value("Circasia"));
    }

    @Test
    void sinPermisoOConPeriodoInvalido() throws Exception {
        mvc.perform(get("/api/dashboard").with(como("cartera"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").with(como("gestor.circasia"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").param("periodo", "202613").with(como("gerente"))).andExpect(status().is4xxClientError());
    }
}
