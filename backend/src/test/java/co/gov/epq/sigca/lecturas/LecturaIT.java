package co.gov.epq.sigca.lecturas;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.usuarios.PruebaUsuariosBase;

/** P-07 / RN-35: crítica de lecturas, revisión por el asesor y cargue de archivo fila por fila. */
class LecturaIT extends PruebaUsuariosBase {

    private static String periodo(YearMonth m) {
        return "%d%02d".formatted(m.getYear(), m.getMonthValue());
    }

    private String registrar(String serial, YearMonth m, int lectura) throws Exception {
        return mvc.perform(post("/api/lecturas").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serial":"%s","periodo":"%s","fechaLectura":"%s","lecturaActual":%d}
                        """.formatted(serial, periodo(m), m.atEndOfMonth(), lectura)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void lecturasHastaRevisionYCargueConFilaMala() throws Exception {
        YearMonth base = YearMonth.now().minusMonths(8);
        Long usuario = crearUsuario();
        Long predio = crearPredio();
        String serial = serialUnico("LEC");
        Long medidor = instalarMedidor(predio, "ACUEDUCTO", serial, base.atDay(1));
        crearContrato(usuario, predio, "ACUEDUCTO");

        // Consumos 10, 10 y 30: la tercera se desvía 200 % del promedio
        String r1 = registrar(serial, base, 10);
        assertTrue(r1.contains("\"estado\":\"CONFIRMADA\""), r1);
        registrar(serial, base.plusMonths(1), 20);
        String r3 = registrar(serial, base.plusMonths(2), 50);
        assertTrue(r3.contains("\"estado\":\"EN_REVISION\""), r3);
        Long enRevision = ((Number) JsonPath.read(r3, "$.id")).longValue();
        mvc.perform(get("/api/lecturas/{id}", enRevision).with(como("asesor")))
                .andExpect(jsonPath("$.promedioReferencia").value(10.0))
                .andExpect(jsonPath("$.desviacionPct").value(200.0))
                .andExpect(jsonPath("$.consumo").value(30.0));
        Integer avisos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.NOTIFICACION WHERE EVENTO = 'LECTURA_EN_REVISION'
                AND ENTIDAD = 'LECTURA' AND ENTIDAD_ID = ?""", Integer.class, enRevision);
        assertTrue(avisos != null && avisos > 0, "Debe avisar a los asesores del municipio");

        // Con la lectura en revisión el medidor no admite la siguiente
        mvc.perform(post("/api/lecturas").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serial":"%s","periodo":"%s","fechaLectura":"%s","lecturaActual":60}
                        """.formatted(serial, periodo(base.plusMonths(3)), base.plusMonths(3).atEndOfMonth())))
                .andExpect(status().is4xxClientError());

        mvc.perform(get("/api/lecturas/revision").param("municipioId", circasia().toString()).with(como("asesor")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lecturas/revision").with(como("gestor.circasia")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/lecturas/{id}/revision", enRevision).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ESTIMAR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/lecturas/{id}/revision", enRevision).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"CONFIRMAR\",\"observacion\":\"Se verificó en terreno\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.revisadaPor", notNullValue()));
        mvc.perform(post("/api/lecturas/{id}/revision", enRevision).with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"CONFIRMAR\"}"))
                .andExpect(status().isConflict());

        // Archivo: dos filas buenas y una con serial inexistente en medio
        YearMonth m4 = base.plusMonths(3);
        YearMonth m5 = base.plusMonths(4);
        String csv = """
                serial,periodo,fecha,lectura,causa
                %s,%s,%s,66,
                NOEXISTE-%s,%s,%s,10,
                %s,%s,%s,82,
                """.formatted(serial, periodo(m4), m4.atEndOfMonth(), unico(), periodo(m4), m4.atEndOfMonth(),
                serial, periodo(m5), m5.atEndOfMonth());
        mvc.perform(multipart("/api/lecturas/archivo")
                .file(new MockMultipartFile("archivo", "lecturas.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesadas").value(3))
                .andExpect(jsonPath("$.registradas").value(2))
                .andExpect(jsonPath("$.conError").value(1))
                .andExpect(jsonPath("$.filas[1].exito").value(false))
                .andExpect(jsonPath("$.filas[1].mensaje", notNullValue()))
                .andExpect(jsonPath("$.filas[2].exito").value(true));
        mvc.perform(multipart("/api/lecturas/archivo")
                .file(new MockMultipartFile("archivo", "l.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .with(como("gestor.circasia")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/lecturas").param("medidorId", medidor.toString()).with(como("asesor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].periodo").value(Integer.parseInt(periodo(m5))))
                .andExpect(jsonPath("$.content[0].origen").value("ARCHIVO"));
        Integer auditorias = jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION IN ('REGISTRAR_LECTURA','REVISAR_LECTURA')
                AND ENTIDAD_ID = ?""", Integer.class, enRevision);
        assertTrue(auditorias != null && auditorias >= 2, "Registro y revisión auditados");
    }

    @Test
    void sinLecturaSeEstimaConElPromedio() throws Exception {
        YearMonth base = YearMonth.now().minusMonths(4);
        Long predio = crearPredio();
        String serial = serialUnico("EST");
        instalarMedidor(predio, "GAS", serial, base.atDay(1));
        registrar(serial, base, 12);
        mvc.perform(post("/api/lecturas").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serial":"%s","periodo":"%s","fechaLectura":"%s","causaNoLectura":"PREDIO_CERRADO"}
                        """.formatted(serial, periodo(base.plusMonths(1)), base.plusMonths(1).atEndOfMonth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ESTIMADA"))
                .andExpect(jsonPath("$.consumo").value(12.0));
        // Fecha fuera de la ventana del periodo
        mvc.perform(post("/api/lecturas").with(como("asesor")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serial":"%s","periodo":"%s","fechaLectura":"%s","lecturaActual":30}
                        """.formatted(serial, periodo(base.plusMonths(2)), base.atDay(5))))
                .andExpect(status().isBadRequest());
    }
}
