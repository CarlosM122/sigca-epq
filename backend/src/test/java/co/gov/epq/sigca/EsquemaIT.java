package co.gov.epq.sigca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import co.gov.epq.sigca.soporte.PruebaIntegracion;

/** Arranca el contexto con ddl-auto=validate contra el esquema oficial y consulta un catálogo público. */
class EsquemaIT extends PruebaIntegracion {

    @Test
    void elMapeoCoincideConElEsquemaYLosMunicipiosSonNueve() throws Exception {
        mvc.perform(get("/api/publico/municipios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
    }
}
