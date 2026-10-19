package co.gov.epq.sigca.usuarios;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** RF-18 / P-06: estrato según uso, ficha catastral de 30 dígitos, sector del municipio y medidores. */
class PredioIT extends PruebaUsuariosBase {

    private String predio(String uso, String estrato, String ficha, String sector, Long municipio) {
        return """
                {"direccion":"Calle 3 # 4-%s","municipioId":%d,"uso":"%s","estrato":%s,"fichaCatastral":%s,"sectorId":%s}
                """.formatted(System.nanoTime() % 1000, municipio, uso, estrato, ficha == null ? "null" : "\"" + ficha + "\"", sector);
    }

    @Test
    void residencialSinEstratoYComercialConEstratoSon400() throws Exception {
        mvc.perform(post("/api/predios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(predio("RESIDENCIAL", "null", null, "null", circasia())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("estrato")));
        mvc.perform(post("/api/predios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(predio("COMERCIAL", "3", null, "null", circasia())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("estrato")));
        mvc.perform(post("/api/predios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(predio("RESIDENCIAL", "2", "12345678901234567890", "null", circasia())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("fichaCatastral")));
    }

    @Test
    void sectorDeOtroMunicipioSeRechazaYElPredioValidoSeCrea() throws Exception {
        Long sectorBuenavista = jdbc.queryForObject("""
                SELECT S.ID FROM SIGCA.SECTOR S JOIN SIGCA.MUNICIPIO M ON M.ID = S.MUNICIPIO_ID WHERE M.CODIGO_DANE = '63111' AND S.ACTIVO = 'S'
                FETCH FIRST 1 ROW ONLY""", Long.class);
        Long buenavista = jdbc.queryForObject("SELECT ID FROM SIGCA.MUNICIPIO WHERE CODIGO_DANE = '63111'", Long.class);
        mvc.perform(post("/api/predios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(predio("RESIDENCIAL", "1", null, String.valueOf(sectorBuenavista), circasia())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SECTOR_INVALIDO"));

        String ficha = "63111" + String.format("%025d", Math.abs(System.nanoTime()));
        mvc.perform(post("/api/predios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(predio("RESIDENCIAL", "1", ficha, String.valueOf(sectorBuenavista), buenavista)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoPredio", matchesPattern("^PRD-\\d{8}$")))
                .andExpect(jsonPath("$.sector").exists());
        mvc.perform(post("/api/predios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(predio("OFICIAL", "null", ficha, "null", buenavista)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    void medidorDeAlcantarilladoNoSeInstalaYUnoActivoPorServicio() throws Exception {
        Long predio = crearPredio();
        mvc.perform(post("/api/predios/{id}/medidores", predio).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serial":"%s","tipoServicio":"ALCANTARILLADO","fechaInstalacion":"%s","lecturaInicial":0}
                        """.formatted(serialUnico("ALC"), LocalDate.now())))
                .andExpect(status().isBadRequest());
        Long m1 = instalarMedidor(predio, "ACUEDUCTO", serialUnico("ACU"), LocalDate.now().minusDays(10));
        mvc.perform(post("/api/predios/{id}/medidores", predio).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serial":"%s","tipoServicio":"ACUEDUCTO","fechaInstalacion":"%s","lecturaInicial":0}
                        """.formatted(serialUnico("ACU"), LocalDate.now())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/medidores/{id}/retiro", m1).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fechaRetiro\":\"%s\"}".formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADO"));
        mvc.perform(get("/api/predios/{id}", predio).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medidores.length()").value(1));
    }
}
