package co.gov.epq.sigca.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import co.gov.epq.sigca.facturacion.PruebaFacturacionBase;

/** Pago en línea desde el portal con la pasarela simulada y el webhook firmado (CU-04, RN-15, P-18). */
class PagoEnLineaIT extends PruebaFacturacionBase {

    @Autowired
    FirmaWebhook firma;

    private String iniciar(Escenario e, Long facturaId) throws Exception {
        String r = mvc.perform(post("/api/portal/pagos").with(ciudadano(e.usuarioId())).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipoObligacion":"FACTURA","obligacionId":%d}""".formatted(facturaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenciaPago").value(numeroFactura(facturaId)))
                .andExpect(jsonPath("$.monto").value(169820.0))
                .andReturn().getResponse().getContentAsString();
        String referencia = JsonPath.read(r, "$.referencia");
        assertThat((String) JsonPath.read(r, "$.urlCheckout")).endsWith("/" + referencia);
        return referencia;
    }

    private static byte[] aprobacion(String referencia, String codigo) {
        return """
                {"referencia":"%s","estado":"APROBADA","codigoTransaccion":"%s","codigoRespuesta":"00","mensaje":"Aprobada","monto":169820.00}"""
                .formatted(referencia, codigo).getBytes(StandardCharsets.UTF_8);
    }

    private String estadoTx(String referencia) {
        return jdbc.queryForObject("SELECT ESTADO FROM SIGCA.TRANSACCION_PASARELA WHERE REFERENCIA = ?", String.class, referencia);
    }

    @Test
    void webhookConFirmaValidaPagaLaFacturaYEsIdempotente() throws Exception {
        Escenario e = escenarioFacturable();
        Long facturaId = emitirFactura(e);
        String ref = iniciar(e, facturaId);
        // Un segundo clic reutiliza el intento abierto
        assertThat(iniciar(e, facturaId)).isEqualTo(ref);
        assertThat(estadoTx(ref)).isEqualTo("INICIADA");

        byte[] cuerpo = aprobacion(ref, "IT-" + unico());
        mvc.perform(post("/api/publico/pasarela/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Firma-Pasarela", firma.firmar(cuerpo)).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.repetida").value(false));

        assertThat(estadoFactura(facturaId)).isEqualTo("PAGADA");
        var tx = jdbc.queryForMap("SELECT ID, ESTADO, FIRMA_VALIDADA FROM SIGCA.TRANSACCION_PASARELA WHERE REFERENCIA = ?", ref);
        assertThat(tx.get("ESTADO")).isEqualTo("APROBADA");
        assertThat(tx.get("FIRMA_VALIDADA")).isEqualTo("S");
        long txId = ((Number) tx.get("ID")).longValue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.PAGO WHERE TRANSACCION_PASARELA_ID = ? AND CANAL_PAGO = 'EN_LINEA'""",
                Integer.class, txId)).isEqualTo(1);

        // La pasarela reintenta la misma notificación: no cambia nada
        mvc.perform(post("/api/publico/pasarela/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Firma-Pasarela", firma.firmar(cuerpo)).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetida").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.PAGO WHERE FACTURA_ID = ?", Integer.class, facturaId))
                .isEqualTo(1);

        // La página de retorno consulta el resultado en el servidor
        mvc.perform(get("/api/publico/pagos/{ref}", ref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.codigoPago").isNotEmpty());
        mvc.perform(get("/api/portal/pagos").with(ciudadano(e.usuarioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void webhookConFirmaInvalidaResponde401YNoCambiaNada() throws Exception {
        Escenario e = escenarioFacturable();
        Long facturaId = emitirFactura(e);
        String ref = iniciar(e, facturaId);
        byte[] cuerpo = aprobacion(ref, "IT-" + unico());

        mvc.perform(post("/api/publico/pasarela/webhook").contentType(MediaType.APPLICATION_JSON)
                .header("X-Firma-Pasarela", "00" + firma.firmar(cuerpo).substring(2)).content(cuerpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/publico/pasarela/webhook").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());

        assertThat(estadoTx(ref)).isEqualTo("INICIADA");
        assertThat(estadoFactura(facturaId)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT FIRMA_VALIDADA FROM SIGCA.TRANSACCION_PASARELA WHERE REFERENCIA = ?",
                String.class, ref)).isEqualTo("N");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SIGCA.PAGO WHERE FACTURA_ID = ?", Integer.class, facturaId))
                .isZero();
    }

    @Test
    void laPasarelaSimuladaRechazaYElCiudadanoPuedeReintentar() throws Exception {
        Escenario e = escenarioFacturable();
        Long facturaId = emitirFactura(e);
        String ref = iniciar(e, facturaId);

        mvc.perform(get("/api/publico/pasarela-simulada/{ref}", ref))
                .andExpect(status().isOk());
        mvc.perform(post("/api/publico/pasarela-simulada/{ref}/rechazar", ref))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/pago/resultado?referencia=" + ref)));

        assertThat(estadoTx(ref)).isEqualTo("RECHAZADA");
        assertThat(estadoFactura(facturaId)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM SIGCA.LOG_AUDITORIA L JOIN SIGCA.TRANSACCION_PASARELA T ON T.ID = L.ENTIDAD_ID
                WHERE L.OPERACION = 'PAGO_RECHAZADO' AND L.ENTIDAD = 'TRANSACCION_PASARELA' AND T.REFERENCIA = ?""",
                Integer.class, ref)).isEqualTo(1);

        String nueva = iniciar(e, facturaId);
        assertThat(nueva).isNotEqualTo(ref);
        mvc.perform(post("/api/publico/pasarela-simulada/{ref}/aprobar", nueva))
                .andExpect(status().isSeeOther());
        assertThat(estadoFactura(facturaId)).isEqualTo("PAGADA");
    }

    @Test
    void unCiudadanoNoVeNiPagaFacturasAjenas() throws Exception {
        Escenario e = escenarioFacturable();
        Long facturaId = emitirFactura(e);
        Long otro = crearUsuario();
        mvc.perform(get("/api/portal/facturas/{id}", facturaId).with(ciudadano(otro)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/portal/pagos").with(ciudadano(otro)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipoObligacion":"FACTURA","obligacionId":%d}""".formatted(facturaId)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/portal/facturas").with(ciudadano(e.usuarioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(facturaId))
                .andExpect(jsonPath("$[0].lineas.length()").value(2));
        mvc.perform(get("/api/portal/contratos").with(ciudadano(e.usuarioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
