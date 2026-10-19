package co.gov.epq.sigca.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.common.validation.DocumentoIdentidad;

/** RF-01, RF-02, RF-03, RF-16: alta con validación del documento, unicidad, máscara, búsqueda e inactivación. */
class UsuarioIT extends PruebaUsuariosBase {

    @Test
    void documentoInvalidoResponde400ConElCampo() throws Exception {
        mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("NATURAL", "CC", "0123", "Pedro Gómez", circasia())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("numeroIdentificacion")));

        // NIT con dígito de verificación errado
        mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("JURIDICA", "NIT", "900123456-1", "Comercial Andina S.A.S.", circasia())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("numeroIdentificacion")));

        // NIT con persona natural
        mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("NATURAL", "NIT", "900123456-8", "Pedro Gómez", circasia())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("tipoIdentificacion")));
    }

    @Test
    void altaValidaDevuelveSoloLaMascaraYElDuplicadoEs409() throws Exception {
        String cedula = cedulaUnica();
        String r = mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("NATURAL", "CC", cedula, "  Ana   María Ñúñez ", circasia())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo", matchesPattern("^USR-\\d{8}$")))
                .andExpect(jsonPath("$.identificacionMascara").value("******" + cedula.substring(6)))
                .andExpect(jsonPath("$.nombreCompleto").value("Ana María Ñúñez"))
                .andExpect(jsonPath("$.estadoVerificacionRnec").value("VERIFICADO"))
                .andReturn().getResponse().getContentAsString();
        assertThat(r).doesNotContain(cedula);
        Long id = ((Number) JsonPath.read(r, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'CREAR_USUARIO' AND ENTIDAD_ID = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT NUMERO_IDENTIFICACION_CIFRADO FROM SIGCA.USUARIO WHERE ID = ?", String.class, id))
                .doesNotContain(cedula);

        mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("NATURAL", "CC", cedula, "Otra Persona", circasia())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));

        // Búsqueda exacta por documento (HMAC), sin indicar el tipo
        mvc.perform(get("/api/usuarios").param("numeroIdentificacion", cedula).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));

        mvc.perform(get("/api/usuarios/{id}", id).with(como("gestor.circasia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.cuentaPortal").value("SIN_CUENTA"))
                .andExpect(jsonPath("$.contratos.length()").value(0));
    }

    @Test
    void rnecCaidaDejaPendienteConAdvertenciaYNitJuridicoEsValido() throws Exception {
        String base = String.format("%08d", Math.abs(System.nanoTime()) % 100_000_000L);
        String nit = "9" + base;
        nit = nit + "-" + DocumentoIdentidad.digitoVerificacionNit(nit);
        mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("JURIDICA", "NIT", nit, "Café del Quindío S.A.S.", circasia())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoPersona").value("JURIDICA"))
                .andExpect(jsonPath("$.identificacionMascara", startsWith("*")));

        String pasaporte = "PA" + base + "19999";
        mvc.perform(post("/api/usuarios").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("NATURAL", "PA", pasaporte, "Luis Pendiente", circasia())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoVerificacionRnec").value("PENDIENTE"))
                .andExpect(jsonPath("$.advertencia").exists());
    }

    @Test
    void modificarEInactivarQuedanAuditados() throws Exception {
        Long id = crearUsuario();
        Long version = jdbc.queryForObject("SELECT VERSION FROM SIGCA.USUARIO WHERE ID = ?", Long.class, id);
        mvc.perform(put("/api/usuarios/{id}", id).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombreCompleto":"María José Pérez Ruiz","correo":"NUEVO@Correo.com","telefono":"+576067400000",
                         "direccionNotificacion":"Calle 11 # 6-30","municipioId":%d,"version":%d}
                        """.formatted(circasia(), version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("nuevo@correo.com"))
                .andExpect(jsonPath("$.telefono").value("6067400000"));

        // Versión vieja: conflicto de concurrencia
        mvc.perform(put("/api/usuarios/{id}/contacto", id).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"correo":"otro@correo.com","telefono":"3009998877","direccionNotificacion":"Calle 1 # 1-1","version":%d}
                        """.formatted(version)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/usuarios/{id}/inactivacion", id).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Solicitud del titular\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE ENTIDAD = 'USUARIO' AND ENTIDAD_ID = ?
                AND OPERACION IN ('MODIFICAR_USUARIO','INACTIVAR_USUARIO')""", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void sinPermisoResponde403() throws Exception {
        mvc.perform(post("/api/usuarios").with(como("gerente")).contentType(MediaType.APPLICATION_JSON)
                .content(jsonUsuario("NATURAL", "CC", cedulaUnica(), "Sin Permiso", circasia())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(ciudadanoCualquiera()))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor ciudadanoCualquiera() throws Exception {
        return ciudadano(crearUsuario());
    }
}
