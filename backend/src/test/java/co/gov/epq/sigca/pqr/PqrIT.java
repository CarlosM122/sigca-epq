package co.gov.epq.sigca.pqr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.soporte.PruebaIntegracion;

/** PQR de punta a punta contra el Oracle compartido: radicación por los tres canales, trámite, reclamo y recurso. */
class PqrIT extends PruebaIntegracion {

    @Autowired
    CifradorIdentificacion cifrador;

    DatosAtencion datos;

    @BeforeEach
    void preparar() {
        datos = new DatosAtencion(jdbc, cifrador);
    }

    static byte[] pdf(String marca) {
        return ("%PDF-1.4\n% " + marca + "\n").getBytes(StandardCharsets.US_ASCII);
    }

    static String radicar(String tipo, String contrato, String extra) {
        return """
                {"tipo":"%s",%s"nombreContacto":"Ana María Pérez","correoContacto":"ana.perez@correo.co",
                 "telefonoContacto":"3105551234","descripcion":"El servicio de acueducto llega con muy baja presión desde hace una semana."%s}"""
                .formatted(tipo, contrato == null ? "" : "\"numeroContrato\":\"" + contrato + "\",", extra == null ? "" : "," + extra);
    }

    static MockMultipartFile datosJson(String json) {
        return new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    MockMultipartHttpServletRequestBuilder conDatos(String url, String json, byte[]... adjuntos) {
        MockMultipartHttpServletRequestBuilder b = multipart(url);
        b.file(datosJson(json));
        int i = 0;
        for (byte[] a : adjuntos) {
            b.file(new MockMultipartFile("adjuntos", "soporte" + (i++) + ".pdf", "application/pdf", a));
        }
        return b;
    }

    String cuerpo(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    Map<String, Object> pqr(String radicado) {
        return jdbc.queryForMap("SELECT * FROM SIGCA.PQR WHERE NUMERO_RADICADO = ?", radicado);
    }

    List<Long> gestoresDe(long municipio) {
        return jdbc.queryForList("""
                SELECT F.ID FROM SIGCA.FUNCIONARIO F JOIN SIGCA.FUNCIONARIO_ROL FR ON FR.FUNCIONARIO_ID = F.ID
                  JOIN SIGCA.ROL R ON R.ID = FR.ROL_ID
                 WHERE R.CODIGO = 'GESTOR_PQR' AND F.ACTIVO = 'S' AND F.MUNICIPIO_ID = ?""", Long.class, municipio);
    }

    long notificaciones(long pqrId, String evento) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.NOTIFICACION WHERE ENTIDAD = 'PQR' AND ENTIDAD_ID = ? AND EVENTO = ?",
                Long.class, pqrId, evento);
    }

    @Test
    void radicaPorLosTresCanalesYAsignaAlGestorDelMunicipio() throws Exception {
        DatosAtencion.Titular t = datos.titular("63190");
        List<Long> gestores = gestoresDe(t.municipioId());

        // Portal: ciudadano autenticado sobre su contrato, con soporte
        String r1 = cuerpo(mvc.perform(conDatos("/api/portal/pqr", radicar("QUEJA", t.numeroContrato(), null), pdf("portal" + unico()))
                .with(ciudadano(t.usuarioId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.fechaLimiteRespuesta").exists()));
        Map<String, Object> p1 = pqr(JsonPath.read(r1, "$.numeroRadicado"));
        assertThat(p1.get("CANAL")).isEqualTo("PORTAL");
        assertThat(p1.get("REGISTRADA_POR")).isNull();
        assertThat(((Number) p1.get("USUARIO_ID")).longValue()).isEqualTo(t.usuarioId());
        assertThat(gestores).contains(((Number) p1.get("GESTOR_ID")).longValue());
        long id1 = ((Number) p1.get("ID")).longValue();
        assertThat(notificaciones(id1, "PQR_RADICADA")).isEqualTo(1);
        assertThat(notificaciones(id1, "PQR_ASIGNADA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.PQR_ADJUNTO WHERE PQR_ID = ? AND TIPO = 'SOPORTE'", Long.class, id1))
                .isEqualTo(1);

        // Un contrato ajeno no se acepta en el portal
        DatosAtencion.Titular otro = datos.titular("63190");
        mvc.perform(conDatos("/api/portal/pqr", radicar("QUEJA", otro.numeroContrato(), null)).with(ciudadano(t.usuarioId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRATO_NO_VALIDO"));

        // Presencial: la registra el gestor en ventanilla
        String r2 = cuerpo(mvc.perform(conDatos("/api/pqr", radicar("PETICION", t.numeroContrato(), null))
                .with(como("gestor.circasia")))
                .andExpect(status().isCreated()));
        Map<String, Object> p2 = pqr(JsonPath.read(r2, "$.numeroRadicado"));
        assertThat(p2.get("CANAL")).isEqualTo("PRESENCIAL");
        assertThat(((Number) p2.get("REGISTRADA_POR")).longValue()).isEqualTo(datos.funcionario("gestor.circasia"));

        // Público sin sesión: petición sin contrato con la identificación del solicitante
        String doc = "1" + String.format("%08d", Long.parseLong(unico()) % 100_000_000L);
        String r3 = cuerpo(mvc.perform(conDatos("/api/publico/pqr", radicar("PETICION", null,
                "\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + doc + "\",\"municipioId\":" + t.municipioId()
                        + ",\"autorizaTratamientoDatos\":true")))
                .andExpect(status().isCreated()));
        String radicado3 = JsonPath.read(r3, "$.numeroRadicado");
        Map<String, Object> p3 = pqr(radicado3);
        assertThat(p3.get("CANAL")).isEqualTo("PORTAL");
        assertThat(p3.get("REGISTRADA_POR")).isNull();
        assertThat(p3.get("CONTRATO_ID")).isNull();
        assertThat(p3.get("SOLICITANTE_IDENTIFICACION_HASH")).isEqualTo(cifrador.hash(
                co.gov.epq.sigca.common.validation.TipoIdentificacion.CC, doc));
        assertThat((String) p3.get("SOLICITANTE_IDENTIFICACION_CIFRADO")).doesNotContain(doc);

        // Público: una queja exige contrato, y con contrato la identificación debe ser la del titular
        mvc.perform(conDatos("/api/publico/pqr", radicar("QUEJA", null,
                "\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + doc + "\",\"municipioId\":" + t.municipioId()
                        + ",\"autorizaTratamientoDatos\":true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(conDatos("/api/publico/pqr", radicar("QUEJA", t.numeroContrato(),
                "\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + doc + "\",\"autorizaTratamientoDatos\":true")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("IDENTIFICACION_NO_COINCIDE"));
        mvc.perform(conDatos("/api/publico/pqr", radicar("QUEJA", t.numeroContrato(),
                "\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + t.documento() + "\",\"autorizaTratamientoDatos\":true")))
                .andExpect(status().isCreated());

        // Consulta pública: radicado + identificación; otra identificación no revela nada
        mvc.perform(post("/api/publico/pqr/consulta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"radicado\":\"" + radicado3 + "\",\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + doc + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECIBIDA"));
        mvc.perform(post("/api/publico/pqr/consulta").contentType(MediaType.APPLICATION_JSON)
                .content("{\"radicado\":\"" + radicado3 + "\",\"tipoIdentificacion\":\"CC\",\"numeroIdentificacion\":\"" + t.documento() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PQR_NO_ENCONTRADA"));

        // Mis PQR del portal: solo las del titular
        mvc.perform(get("/api/portal/pqr").with(ciudadano(t.usuarioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroRadicado == '" + p1.get("NUMERO_RADICADO") + "')]").exists())
                .andExpect(jsonPath("$[?(@.numeroRadicado == '" + radicado3 + "')]").doesNotExist());

        // Adjunto: descarga segura desde la bandeja
        Long adj = jdbc.queryForObject("SELECT ID FROM SIGCA.PQR_ADJUNTO WHERE PQR_ID = ?", Long.class, id1);
        mvc.perform(get("/api/pqr/" + id1 + "/adjuntos/" + adj).with(como("gestor.circasia")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"));

        // Auditoría de la radicación
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'REGISTRAR_PQR' AND ENTIDAD_ID = ?",
                Long.class, id1)).isEqualTo(1);
    }

    @Test
    void responderExigeEvidenciaYAccesoPorMunicipioYPermiso() throws Exception {
        DatosAtencion.Titular t = datos.titular("63190");
        String r = cuerpo(mvc.perform(conDatos("/api/portal/pqr", radicar("PETICION", t.numeroContrato(), null))
                .with(ciudadano(t.usuarioId()))).andExpect(status().isCreated()));
        long id = ((Number) JsonPath.read(r, "$.id")).longValue();

        // 403 por permiso (asesor no tramita PQR; un ciudadano tampoco) y por municipio (gestor de Salento)
        mvc.perform(get("/api/pqr").with(como("asesor"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/pqr/" + id).with(ciudadano(t.usuarioId()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/pqr/" + id).with(como("gestor.salento")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(post("/api/pqr/" + id + "/tomar").with(como("gestor.salento"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/pqr/" + id + "/reasignacion").with(como("gestor.circasia")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"gestorId\":1,\"motivo\":\"Prueba de permiso\"}")).andExpect(status().isForbidden());
        // El gestor de Salento no la ve en su bandeja; el jefe (todas) sí
        String bandejaSalento = cuerpo(mvc.perform(get("/api/pqr").param("size", "100").param("radicado", (String) JsonPath.read(r, "$.numeroRadicado"))
                .with(como("gestor.salento"))).andExpect(status().isOk()));
        assertThat((Integer) JsonPath.read(bandejaSalento, "$.page.totalElements")).isZero();
        mvc.perform(get("/api/pqr").param("radicado", (String) JsonPath.read(r, "$.numeroRadicado")).with(como("jefe.pqr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mvc.perform(post("/api/pqr/" + id + "/tomar").with(como("gestor.circasia")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_TRAMITE"));
        mvc.perform(post("/api/pqr/" + id + "/actuaciones").with(como("gestor.circasia")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\":\"Se programó visita técnica al predio.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actuaciones.length()").value(1));

        String respuesta = "{\"respuesta\":\"Se revisó la red y la presión quedó normalizada en el sector.\"}";
        mvc.perform(post("/api/pqr/" + id + "/respuesta").with(como("gestor.circasia")).contentType(MediaType.APPLICATION_JSON)
                .content(respuesta))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_EVIDENCIA"));

        // Archivo que no es PDF/JPG/PNG: rechazado por el contenido
        mvc.perform(multipart("/api/pqr/" + id + "/evidencias")
                .file(new MockMultipartFile("archivos", "acta.pdf", "application/pdf", "MZ ejecutable".getBytes(StandardCharsets.UTF_8)))
                .with(como("gestor.circasia")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ADJUNTO_INVALIDO"));
        mvc.perform(multipart("/api/pqr/" + id + "/evidencias")
                .file(new MockMultipartFile("archivos", "acta.pdf", "application/pdf", pdf("evidencia" + unico())))
                .with(como("gestor.circasia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjuntos[?(@.tipo == 'EVIDENCIA')]").exists());

        mvc.perform(post("/api/pqr/" + id + "/respuesta").with(como("gestor.circasia")).contentType(MediaType.APPLICATION_JSON)
                .content(respuesta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESPONDIDA"))
                .andExpect(jsonPath("$.historial.length()").value(3));
        assertThat(notificaciones(id, "PQR_RESPONDIDA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'RESPONDER_PQR' AND ENTIDAD_ID = ?",
                Long.class, id)).isEqualTo(1);
        // Respondida es final
        mvc.perform(post("/api/pqr/" + id + "/respuesta").with(como("gestor.circasia")).contentType(MediaType.APPLICATION_JSON)
                .content(respuesta)).andExpect(status().isConflict());
    }

    @Test
    void reclamoSobreLineaConAjusteYRecursoConApelacionALaSspd() throws Exception {
        DatosAtencion.Titular t = datos.titular("63190");
        DatosAtencion.FacturaCreada f = datos.factura(t, DatosAtencion.periodos().getLast(), 120);
        BigDecimal totalLinea = jdbc.queryForObject("SELECT TOTAL_LINEA FROM SIGCA.LINEA_FACTURA WHERE ID = ?", BigDecimal.class, f.lineaId());

        RequestPostProcessor ciudadano = ciudadano(t.usuarioId());
        // El contexto del portal ofrece la factura como reclamable
        mvc.perform(get("/api/portal/pqr/contexto").with(ciudadano))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contratos[0].facturas[?(@.numeroFactura == '" + f.numero() + "')]").exists());

        String r = cuerpo(mvc.perform(conDatos("/api/portal/pqr", radicar("RECLAMO", t.numeroContrato(),
                "\"numeroFactura\":\"" + f.numero() + "\""), pdf("reclamo" + unico())).with(ciudadano))
                .andExpect(status().isCreated()));
        String radicado = JsonPath.read(r, "$.numeroRadicado");
        long id = ((Number) JsonPath.read(r, "$.id")).longValue();
        assertThat(((Number) pqr(radicado).get("LINEA_FACTURA_ID")).longValue()).isEqualTo(f.lineaId());
        assertThat(jdbc.queryForObject("SELECT EN_RECLAMO FROM SIGCA.LINEA_FACTURA WHERE ID = ?", String.class, f.lineaId())).isEqualTo("S");
        assertThat(jdbc.queryForObject("SELECT VALOR_EN_RECLAMO FROM SIGCA.FACTURA WHERE ID = ?", BigDecimal.class, f.id()))
                .isEqualByComparingTo(totalLinea);

        RequestPostProcessor gestor = como("gestor.circasia");
        mvc.perform(post("/api/pqr/" + id + "/ajuste-linea").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorAjuste\":-10000}")).andExpect(status().isConflict()); // aún RECIBIDA
        mvc.perform(post("/api/pqr/" + id + "/tomar").with(gestor)).andExpect(status().isOk());
        mvc.perform(post("/api/pqr/" + id + "/ajuste-linea").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorAjuste\":10000}")).andExpect(status().isBadRequest()); // debe ser negativo
        mvc.perform(post("/api/pqr/" + id + "/ajuste-linea").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valorAjuste\":-10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linea.valorAjuste").value(-10000.0));
        assertThat(jdbc.queryForObject("SELECT VALOR_AJUSTE FROM SIGCA.LINEA_FACTURA WHERE ID = ?", BigDecimal.class, f.lineaId()))
                .isEqualByComparingTo("-10000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'AJUSTAR_LINEA' AND ENTIDAD_ID = ?",
                Long.class, f.lineaId())).isEqualTo(1);

        mvc.perform(multipart("/api/pqr/" + id + "/evidencias")
                .file(new MockMultipartFile("archivos", "liquidacion.pdf", "application/pdf", pdf("liq" + unico()))).with(gestor))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pqr/" + id + "/respuesta").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"Se reliquidó el consumo y se descontaron diez mil pesos de la factura.\"}"))
                .andExpect(status().isOk());
        // Resuelto el reclamo, la línea sale de reclamo con el ajuste aplicado
        assertThat(jdbc.queryForObject("SELECT EN_RECLAMO FROM SIGCA.LINEA_FACTURA WHERE ID = ?", String.class, f.lineaId())).isEqualTo("N");

        // Recurso de reposición con apelación desde el portal
        mvc.perform(get("/api/portal/pqr").with(ciudadano))
                .andExpect(jsonPath("$[?(@.numeroRadicado == '" + radicado + "')].puedeRecurrir").value(true));
        String rr = cuerpo(mvc.perform(conDatos("/api/portal/pqr", radicar("RECURSO", t.numeroContrato(),
                "\"radicadoOriginal\":\"" + radicado + "\",\"solicitaApelacion\":true")).with(ciudadano))
                .andExpect(status().isCreated()));
        long recurso = ((Number) JsonPath.read(rr, "$.id")).longValue();
        Map<String, Object> pr = pqr(JsonPath.read(rr, "$.numeroRadicado"));
        assertThat(((Number) pr.get("PQR_ORIGEN_ID")).longValue()).isEqualTo(id);
        assertThat(((Number) pr.get("LINEA_FACTURA_ID")).longValue()).isEqualTo(f.lineaId());
        assertThat(pr.get("SOLICITA_APELACION")).isEqualTo("S");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'REGISTRAR_RECURSO' AND ENTIDAD_ID = ?",
                Long.class, recurso)).isEqualTo(1);
        // Solo un recurso por respuesta
        mvc.perform(conDatos("/api/portal/pqr", radicar("RECURSO", t.numeroContrato(),
                "\"radicadoOriginal\":\"" + radicado + "\",\"solicitaApelacion\":false")).with(ciudadano))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/pqr/" + recurso + "/tomar").with(gestor)).andExpect(status().isOk());
        mvc.perform(multipart("/api/pqr/" + recurso + "/evidencias")
                .file(new MockMultipartFile("archivos", "analisis.pdf", "application/pdf", pdf("rec" + unico()))).with(gestor))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pqr/" + recurso + "/respuesta").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"Se confirma la decisión inicial tras revisar el expediente.\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("DECISION_REQUERIDA"));
        mvc.perform(post("/api/pqr/" + recurso + "/respuesta").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"Se confirma la decisión inicial tras revisar el expediente.\",\"decisionRecurso\":\"CONFIRMA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REQUIERE_TRASLADO"));
        mvc.perform(post("/api/pqr/" + recurso + "/traslado-sspd").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"respuesta\":\"Se confirma la decisión inicial tras revisar el expediente.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("TRASLADADA_SSPD"))
                .andExpect(jsonPath("$.decisionRecurso").value("CONFIRMA"));
        assertThat(notificaciones(recurso, "PQR_TRASLADADA_SSPD")).isEqualTo(1);
        mvc.perform(post("/api/pqr/" + recurso + "/decision-sspd").with(gestor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decisionSspd\":\"MODIFICA\",\"observacion\":\"Resolución SSPD de prueba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESPONDIDA"))
                .andExpect(jsonPath("$.decisionSspd").value("MODIFICA"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION IN ('TRASLADAR_SSPD','REGISTRAR_DECISION_SSPD') AND ENTIDAD_ID = ?",
                Long.class, recurso)).isEqualTo(2);
    }

    @Test
    void elJefeReasignaYReconoceSoloLoQueProcede() throws Exception {
        DatosAtencion.Titular t = datos.titular("63690");
        String r = cuerpo(mvc.perform(conDatos("/api/portal/pqr", radicar("QUEJA", t.numeroContrato(), null))
                .with(ciudadano(t.usuarioId()))).andExpect(status().isCreated()));
        long id = ((Number) JsonPath.read(r, "$.id")).longValue();
        long jefe = datos.funcionario("jefe.pqr");
        mvc.perform(get("/api/pqr/gestores").param("municipioId", String.valueOf(t.municipioId())).with(como("jefe.pqr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + jefe + ")]").exists());
        // Un gestor de otro municipio no es un responsable válido (lo rechaza la BD: -20015)
        mvc.perform(post("/api/pqr/" + id + "/reasignacion").with(como("jefe.pqr")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"gestorId\":" + datos.funcionario("gestor.circasia") + ",\"motivo\":\"Prueba de reasignación\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/pqr/" + id + "/reasignacion").with(como("jefe.pqr")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"gestorId\":" + jefe + ",\"motivo\":\"Carga del gestor del municipio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestorId").value(jefe));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA WHERE OPERACION = 'REASIGNAR_PQR' AND ENTIDAD_ID = ?",
                Long.class, id)).isEqualTo(1);
        // No hay silencio positivo que reconocer en una PQR en plazo
        mvc.perform(post("/api/pqr/" + id + "/reconocimiento-silencio").with(como("jefe.pqr")).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isConflict());
    }
}
