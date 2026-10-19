package co.gov.epq.sigca.tarifas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import co.gov.epq.sigca.usuarios.PruebaUsuariosBase;

/**
 * P-08: validaciones de tarifas y factores. La base es compartida y la semilla no se toca, así que solo se ejercitan
 * los caminos que no escriben (validación, superposición, vigencia inválida y permisos).
 */
class TarifaIT extends PruebaUsuariosBase {

    private static String jsonTarifa(String tipo, Long municipio, String rangos, LocalDate desde) {
        return """
                {"tipoServicio":"%s","municipioId":%d,"cargoFijo":9500,"valorM3Basico":2100.5%s,
                 "vigenteDesde":"%s","resolucion":"Resolución CRA 999 de 2026"}
                """.formatted(tipo, municipio, rangos, desde);
    }

    private static final String RANGOS = ",\"valorM3Complementario\":2300,\"valorM3Suntuario\":2900,"
            + "\"consumoBasicoM3\":13,\"consumoSuntuarioDesdeM3\":26";

    @Test
    void validacionesYSuperposicion() throws Exception {
        Long circasia = circasia();
        mvc.perform(get("/api/tarifas").param("municipioId", circasia.toString()).param("vigenteEn", LocalDate.now().toString())
                .with(como("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].vigenteHoy").value(true));
        mvc.perform(get("/api/factores-subsidio").param("municipioId", circasia.toString()).with(como("admin")))
                .andExpect(status().isOk());

        // Gas con rangos y acueducto sin rangos
        mvc.perform(post("/api/tarifas").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonTarifa("GAS", circasia, RANGOS, LocalDate.now())))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tarifas").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonTarifa("ACUEDUCTO", circasia, "", LocalDate.now())))
                .andExpect(status().isBadRequest());

        // La semilla tiene una tarifa abierta: crear otra superpuesta se rechaza sin escribir
        mvc.perform(post("/api/tarifas").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonTarifa("ACUEDUCTO", circasia, RANGOS, LocalDate.now())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VIGENCIA_SUPERPUESTA"));
        LocalDate abierta = jdbc.queryForObject("""
                SELECT VIGENTE_DESDE FROM SIGCA.TARIFA WHERE TIPO_SERVICIO = 'ACUEDUCTO' AND MUNICIPIO_ID = ?
                AND VIGENTE_HASTA IS NULL""", LocalDate.class, circasia);
        mvc.perform(post("/api/tarifas/nueva-vigencia").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonTarifa("ACUEDUCTO", circasia, RANGOS, abierta)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("VIGENCIA_INVALIDA"));

        // Topes legales del factor y estrato según uso
        mvc.perform(post("/api/factores-subsidio").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"municipioId":%d,"tipoServicio":"ACUEDUCTO","uso":"RESIDENCIAL","estrato":1,"porcentaje":-80,
                         "vigenteDesde":"%s","acuerdoMunicipal":"Acuerdo 012 de 2026"}
                        """.formatted(circasia, LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("porcentaje"));
        mvc.perform(post("/api/factores-subsidio").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"municipioId":%d,"tipoServicio":"ACUEDUCTO","uso":"COMERCIAL","estrato":3,"porcentaje":50,
                         "vigenteDesde":"%s","acuerdoMunicipal":"Acuerdo 012 de 2026"}
                        """.formatted(circasia, LocalDate.now())))
                .andExpect(status().isBadRequest());

        // Sin TARIFA_GESTIONAR
        mvc.perform(post("/api/tarifas").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonTarifa("ACUEDUCTO", circasia, RANGOS, LocalDate.now())))
                .andExpect(status().isForbidden());
    }
}
