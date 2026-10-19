package co.gov.epq.sigca.contratos;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import co.gov.epq.sigca.usuarios.PruebaUsuariosBase;

/** RF-04, RN-01, P-06, P-11: creación, alcantarillado ligado al acueducto, duplicados y cancelación. */
class ContratoIT extends PruebaUsuariosBase {

    @Test
    void alcantarilladoSinAcueductoLoRechazaLaBaseDeDatos() throws Exception {
        Long usuario = crearUsuario();
        Long predio = crearPredio();
        mvc.perform(post("/api/contratos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonContrato(usuario, predio, "ALCANTARILLADO")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRATO_NO_VALIDO"))
                .andExpect(jsonPath("$.mensaje").value(
                        "El alcantarillado requiere un contrato de acueducto vigente del mismo usuario en el predio"));
    }

    @Test
    void acueductoConMedidorAlcantarilladoYDuplicado() throws Exception {
        Long usuario = crearUsuario();
        Long predio = crearPredio();
        // Sin medidor instalado no hay contrato de acueducto
        mvc.perform(post("/api/contratos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonContrato(usuario, predio, "ACUEDUCTO")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_MEDIDOR"));
        String serial = serialUnico("ACU");
        instalarMedidor(predio, "ACUEDUCTO", serial, LocalDate.now().minusMonths(1));
        Long acueducto = crearContrato(usuario, predio, "ACUEDUCTO");
        mvc.perform(get("/api/contratos/{id}", acueducto).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroContrato", matchesPattern("^CTR-\\d{8}$")))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.serialMedidor").value(serial));
        Long alcantarillado = crearContrato(usuario, predio, "ALCANTARILLADO");

        mvc.perform(post("/api/contratos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonContrato(usuario, predio, "ACUEDUCTO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));

        // El acueducto no se cancela mientras el alcantarillado siga vigente (TRG_CONTRATO_CIU)
        mvc.perform(post("/api/contratos/{id}/cancelacion", acueducto).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Solicitud del usuario\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRATO_NO_VALIDO"));
        mvc.perform(post("/api/contratos/{id}/cancelacion", alcantarillado).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Solicitud del usuario\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        mvc.perform(get("/api/contratos").param("usuarioId", usuario.toString()).with(como("gestor.circasia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
        // El usuario con contratos vigentes no se inactiva (TRG_USUARIO_BIU, P-24)
        mvc.perform(post("/api/usuarios/{id}/inactivacion", usuario).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Solicitud del titular\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("OBLIGACIONES_PENDIENTES"));
    }

    @Test
    void sinPermisoResponde403() throws Exception {
        mvc.perform(post("/api/contratos").with(como("gestor.circasia")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonContrato(1L, 1L, "ACUEDUCTO")))
                .andExpect(status().isForbidden());
    }
}
