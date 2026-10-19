package co.gov.epq.sigca.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.soporte.PruebaIntegracion;

/**
 * Endpoints de administración (RF-14, RF-15, RF-29) con su verificación de permisos. No modifica datos de la
 * semilla compartida: crea sus propios funcionarios, festivos de 2099 y sectores con nombre único.
 */
class AdministracionIT extends PruebaIntegracion {

    @Test
    void sinPermisoDeAdministracionResponde403() throws Exception {
        mvc.perform(get("/api/admin/funcionarios").with(como("asesor"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(get("/api/admin/parametros").with(como("gerente"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auditoria").with(como("jefe.pqr"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/roles/GERENTE/permisos").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permisos\":[]}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/funcionarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void cicloDeVidaDeUnFuncionario() throws Exception {
        String u = "nuevo.func" + unico();
        String cuerpo = """
                {"codigoFuncionario":"NF-%s","nombreCompleto":"Nuevo Funcionario","correo":"%s@epq.gov.co",
                 "telefono":"3001234567","username":"%s","roles":["ASESOR"]}""".formatted(unico(), u, u);
        String creado = mvc.perform(post("/api/admin/funcionarios").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.funcionario.roles[0]").value("ASESOR"))
                .andExpect(jsonPath("$.funcionario.cambioPasswordRequerido").value(true))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(creado, "$.funcionario.id");
        String temporal = JsonPath.read(creado, "$.passwordTemporal");
        assertThat(temporal).matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{12,64}$");
        assertThat(jdbc.queryForObject("SELECT PASSWORD_HASH FROM SIGCA.FUNCIONARIO WHERE ID = ?", String.class, id))
                .startsWith("$2").doesNotContain(temporal);

        // La contraseña temporal sirve para el primer ingreso (que exige enrolar el 2FA)
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + u + "\",\"password\":\"" + temporal + "\"}"))
                .andExpect(jsonPath("$.codigo").value("DOBLE_FACTOR_ENROLAR"));

        mvc.perform(post("/api/admin/funcionarios").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.replace("NF-", "NG-"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));

        roles(id, "[\"CIUDADANO\"]").andExpect(status().isUnprocessableContent());
        roles(id, "[\"ASESOR\",\"GESTOR_PQR\"]").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FUNCIONARIO_SIN_ROL"));
        roles(id, "[]").andExpect(status().isBadRequest());

        mvc.perform(put("/api/admin/funcionarios/" + id).with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"Nuevo Funcionario Editado\",\"correo\":\"" + u
                        + "@epq.gov.co\",\"municipioId\":2,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.municipio").value("Circasia"));
        mvc.perform(put("/api/admin/funcionarios/" + id).with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"Otro\",\"correo\":\"" + u + "@epq.gov.co\",\"municipioId\":2,\"version\":0}"))
                .andExpect(status().isConflict());
        roles(id, "[\"GESTOR_PQR\"]").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1)).andExpect(jsonPath("$.roles[0]").value("GESTOR_PQR"));
        mvc.perform(put("/api/admin/funcionarios/" + id).with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"Nuevo Funcionario Editado\",\"correo\":\"" + u + "@epq.gov.co\",\"version\":1}"))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(post("/api/admin/funcionarios/" + id + "/doble-factor/reinicio").with(como("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dobleFactorActivo").value(false));
        mvc.perform(post("/api/admin/funcionarios/" + id + "/estado").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        assertThat(jdbc.queryForObject("SELECT FECHA_DESACTIVACION FROM SIGCA.FUNCIONARIO WHERE ID = ?", Object.class, id))
                .isNotNull();

        for (String op : List.of("CREAR_FUNCIONARIO", "ASIGNAR_ROL", "REVOCAR_ROL", "MODIFICAR_FUNCIONARIO", "DESACTIVAR_FUNCIONARIO")) {
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = ? AND ENTIDAD = 'FUNCIONARIO' AND ENTIDAD_ID = ?""",
                    Integer.class, op, id)).as(op).isPositive();
        }
        mvc.perform(get("/api/admin/auditoria").with(como("admin")).param("operacion", "CREAR_FUNCIONARIO")
                .param("usuario", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].entidadId").value(hasItem(id)))
                .andExpect(jsonPath("$.content[0].actor").value("admin"));
        mvc.perform(get("/api/admin/funcionarios").with(como("admin")).param("texto", u))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void elAdministradorNoSeQuitaSuPropioAcceso() throws Exception {
        Long admin = jdbc.queryForObject("SELECT ID FROM SIGCA.FUNCIONARIO WHERE USERNAME = 'admin'", Long.class);
        roles(admin.intValue(), "[\"GERENTE\"]").andExpect(jsonPath("$.codigo").value("AUTOGESTION_NO_PERMITIDA"));
        mvc.perform(post("/api/admin/funcionarios/" + admin + "/estado").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(jsonPath("$.codigo").value("AUTOGESTION_NO_PERMITIDA"));
    }

    @Test
    void matrizDePermisos() throws Exception {
        String roles = mvc.perform(get("/api/admin/roles").with(como("admin"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> gerente = JsonPath.read(roles, "$[?(@.codigo == 'GERENTE')].permisos[*]");
        mvc.perform(get("/api/admin/permisos").with(como("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo").value(hasItem("ROL_GESTIONAR")));
        permisos("ADMIN", "[\"FUNCIONARIO_GESTIONAR\"]").andExpect(jsonPath("$.codigo").value("PERMISO_NO_APLICA"));
        permisos("GERENTE", "[\"PORTAL_CONSULTAR\"]").andExpect(jsonPath("$.codigo").value("PERMISO_NO_APLICA"));
        permisos("CIUDADANO", "[\"USUARIO_CREAR\"]").andExpect(jsonPath("$.codigo").value("PERMISO_NO_APLICA"));
        // Reenviar los mismos permisos no cambia nada
        String mismos = "[" + String.join(",", gerente.stream().map(p -> "\"" + p + "\"").toList()) + "]";
        permisos("GERENTE", mismos).andExpect(status().isOk()).andExpect(jsonPath("$.permisos.length()").value(gerente.size()));
    }

    @Test
    void parametrosFestivosYSectores() throws Exception {
        mvc.perform(get("/api/admin/parametros").with(como("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].clave").value(hasItem("PORTAL_MAX_INTENTOS")));
        // Rango legal validado por TRG_PARAMETRO_CIU (P-12: máximo 24 cuotas); la transacción se revierte
        parametro("ACUERDO_MAX_CUOTAS", "30").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        parametro("ACUERDO_MAX_CUOTAS", "doce").andExpect(status().isUnprocessableContent());
        parametro("NO_EXISTE", "1").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT VALOR FROM SIGCA.PARAMETRO_SISTEMA WHERE CLAVE = 'ACUERDO_MAX_CUOTAS'", String.class))
                .isEqualTo("24");

        String fecha = "2099-0" + (1 + (int) (System.nanoTime() % 9)) + "-1" + (int) (System.nanoTime() % 10);
        jdbc.update("DELETE FROM SIGCA.FESTIVO WHERE FECHA = TO_DATE(?, 'YYYY-MM-DD')", fecha);
        mvc.perform(post("/api/admin/festivos").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + fecha + "\",\"descripcion\":\"Festivo de prueba\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/festivos").with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\":\"" + fecha + "\",\"descripcion\":\"Festivo de prueba\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/admin/festivos").with(como("admin")).param("anio", "2099"))
                .andExpect(jsonPath("$[*].fecha").value(hasItem(fecha)));
        mvc.perform(delete("/api/admin/festivos/" + fecha).with(como("admin"))).andExpect(status().isNoContent());
        String pasado = jdbc.queryForObject("SELECT TO_CHAR(MIN(FECHA), 'YYYY-MM-DD') FROM SIGCA.FESTIVO", String.class);
        mvc.perform(delete("/api/admin/festivos/" + pasado).with(como("admin")))
                .andExpect(jsonPath("$.codigo").value("FESTIVO_PASADO"));

        String nombre = "Vereda Prueba " + unico();
        String sector = mvc.perform(post("/api/admin/municipios/1/sectores").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"" + nombre + "\",\"tipo\":\"VEREDA\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.activo").value(true))
                .andReturn().getResponse().getContentAsString();
        Integer sectorId = JsonPath.read(sector, "$.id");
        mvc.perform(post("/api/admin/municipios/1/sectores").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"" + nombre + "\",\"tipo\":\"VEREDA\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/sectores/" + sectorId).with(como("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + " B\",\"tipo\":\"CORREGIMIENTO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("CORREGIMIENTO"));
        mvc.perform(put("/api/admin/sectores/" + sectorId + "/estado").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(get("/api/admin/municipios").with(como("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
    }

    private org.springframework.test.web.servlet.ResultActions roles(Integer id, String roles) throws Exception {
        return mvc.perform(put("/api/admin/funcionarios/" + id + "/roles").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":" + roles + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions permisos(String rol, String permisos) throws Exception {
        return mvc.perform(put("/api/admin/roles/" + rol + "/permisos").with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"permisos\":" + permisos + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions parametro(String clave, String valor) throws Exception {
        return mvc.perform(put("/api/admin/parametros/" + clave).with(como("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"" + valor + "\"}"));
    }
}
