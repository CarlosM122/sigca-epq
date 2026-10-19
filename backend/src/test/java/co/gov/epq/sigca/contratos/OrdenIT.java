package co.gov.epq.sigca.contratos;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.usuarios.PruebaUsuariosBase;

/** P-11 / RN-36: suspensión técnica ejecutada por el asesor y reconexión con plazo de 24 horas. */
class OrdenIT extends PruebaUsuariosBase {

    private Long crearOrden(Long contrato, String tipo, String causa) throws Exception {
        String r = mvc.perform(post("/api/ordenes").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contratoId":%d,"tipo":"%s","causa":"%s","observacion":"Revisión de la acometida"}
                        """.formatted(contrato, tipo, causa)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroOrden", matchesPattern("^OS-\\d{8}$")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    @Test
    void suspensionTecnicaEjecutadaYReconexion() throws Exception {
        Long usuario = crearUsuario();
        Long predio = crearPredio();
        instalarMedidor(predio, "ACUEDUCTO", serialUnico("ACU"), LocalDate.now().minusMonths(1));
        Long contrato = crearContrato(usuario, predio, "ACUEDUCTO");

        // La de mora es de cartera; la reconexión de un contrato activo no procede
        mvc.perform(post("/api/ordenes").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contratoId\":%d,\"tipo\":\"SUSPENSION\",\"causa\":\"MORA\",\"observacion\":\"Mora de dos periodos\"}"
                        .formatted(contrato)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/ordenes").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contratoId\":%d,\"tipo\":\"RECONEXION\",\"causa\":\"TECNICA\",\"observacion\":\"Reconexión\"}"
                        .formatted(contrato)))
                .andExpect(status().isConflict());

        Long suspension = crearOrden(contrato, "SUSPENSION", "TECNICA");
        mvc.perform(get("/api/ordenes").param("tipo", "SUSPENSION").with(como("asesor")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ordenes/{id}/ejecucion", suspension).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"observacion\":\"Se cerró la válvula\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EJECUTADA"))
                .andExpect(jsonPath("$.fechaEjecucion", notNullValue()));
        mvc.perform(get("/api/contratos/{id}", contrato).with(como("asesor")))
                .andExpect(jsonPath("$.estado").value("SUSPENDIDO"));

        Long reconexion = crearOrden(contrato, "RECONEXION", "SOLICITUD_USUARIO");
        mvc.perform(get("/api/ordenes/{id}", reconexion).with(como("asesor")))
                .andExpect(jsonPath("$.fechaLimite", notNullValue()))
                .andExpect(jsonPath("$.vencida").value(false));
        mvc.perform(post("/api/ordenes/{id}/ejecucion", reconexion).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EJECUTADA"));
        mvc.perform(get("/api/contratos/{id}", contrato).with(como("asesor")))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        mvc.perform(get("/api/ordenes").param("contratoId", contrato.toString()).with(como("asesor")))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // Una orden ejecutada no se anula
        mvc.perform(post("/api/ordenes/{id}/anulacion", reconexion).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Error de digitación\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anularOrdenPendienteYPermisos() throws Exception {
        Long usuario = crearUsuario();
        Long predio = crearPredio();
        instalarMedidor(predio, "GAS", serialUnico("GAS"), LocalDate.now().minusMonths(1));
        Long contrato = crearContrato(usuario, predio, "GAS");
        Long orden = crearOrden(contrato, "SUSPENSION", "SOLICITUD_USUARIO");

        mvc.perform(post("/api/ordenes/{id}/ejecucion", orden).with(como("cartera")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ordenes/{id}/anulacion", orden).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"El usuario desistió\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADA"));
        mvc.perform(get("/api/contratos/{id}", contrato).with(como("asesor")))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }
}
