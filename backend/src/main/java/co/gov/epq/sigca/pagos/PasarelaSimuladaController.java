package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.facturacion.DocumentoPdf;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pasarela simulada (P-19), solo en modo {@code simulado}: muestra el checkout de un intento y, al aprobar o rechazar,
 * firma la notificación igual que lo haría la pasarela real y la entrega al mismo receptor del webhook. Luego
 * redirige a la página de resultado del portal, que consulta el estado en el servidor.
 */
@RestController
@RequestMapping("/api/publico/pasarela-simulada")
@ConditionalOnProperty(name = "sigca.integraciones.pasarela.modo", havingValue = "simulado", matchIfMissing = true)
@Validated
public class PasarelaSimuladaController {

    private static final String REF = "^TX-[0-9A-F]{32}$";

    private final PagoEnLineaService servicio;
    private final ReceptorWebhook receptor;
    private final FirmaWebhook firma;
    private final JsonMapper json;
    private final String portal;

    public PasarelaSimuladaController(PagoEnLineaService servicio, ReceptorWebhook receptor, FirmaWebhook firma,
            JsonMapper json, @Value("${sigca.portal.url-base}") String portal) {
        this.servicio = servicio;
        this.receptor = receptor;
        this.firma = firma;
        this.json = json;
        this.portal = portal.endsWith("/") ? portal.substring(0, portal.length() - 1) : portal;
    }

    @GetMapping(value = "/{referencia}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> checkout(@PathVariable @Pattern(regexp = REF) String referencia) {
        TransaccionPasarela t = servicio.transaccion(referencia);
        String ref = HtmlUtils.htmlEscape(t.getReferencia());
        String abierta = t.getEstado() == EstadoTransaccion.INICIADA
                ? """
                  <form method="post" action="%1$s/aprobar"><button class="ok">Aprobar pago</button></form>
                  <form method="post" action="%1$s/rechazar"><button class="no">Rechazar pago</button></form>
                  """.formatted(ref)
                : "<p>Este intento ya está <b>" + HtmlUtils.htmlEscape(t.getEstado().name()) + "</b>.</p>";
        String html = """
                <!doctype html><html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Pasarela simulada</title>
                <style>body{font-family:system-ui,sans-serif;max-width:420px;margin:40px auto;padding:0 16px;color:#1d2733}
                .caja{border:1px solid #d5dbe2;border-radius:8px;padding:20px}dt{color:#5b6773;font-size:13px}dd{margin:0 0 10px;font-weight:600}
                form{display:inline-block;margin:8px 8px 0 0}button{padding:10px 16px;border:0;border-radius:6px;color:#fff;cursor:pointer}
                .ok{background:#1f7a4d}.no{background:#a83232}small{color:#5b6773}</style></head><body>
                <h1>Pasarela de pagos (simulada)</h1><div class="caja"><dl>
                <dt>Referencia de pago</dt><dd>%s</dd><dt>Valor</dt><dd>%s</dd><dt>Transacción</dt><dd>%s</dd></dl>
                %s</div><p><small>Entorno de pruebas: ningún dinero se mueve.</small></p></body></html>
                """.formatted(HtmlUtils.htmlEscape(t.getReferenciaPago()), HtmlUtils.htmlEscape(DocumentoPdf.pesos(t.getMonto())),
                ref, abierta);
        return ResponseEntity.ok()
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'")
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @PostMapping("/{referencia}/aprobar")
    public ResponseEntity<Void> aprobar(@PathVariable @Pattern(regexp = REF) String referencia) {
        return responder(referencia, "APROBADA", "00", "Aprobada");
    }

    @PostMapping("/{referencia}/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable @Pattern(regexp = REF) String referencia) {
        return responder(referencia, "RECHAZADA", "51", "Fondos insuficientes (simulado)");
    }

    private ResponseEntity<Void> responder(String referencia, String estado, String codigo, String mensaje) {
        TransaccionPasarela t = servicio.transaccion(referencia);
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("referencia", t.getReferencia());
        n.put("estado", estado);
        n.put("codigoTransaccion", "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        n.put("codigoRespuesta", codigo);
        n.put("mensaje", mensaje);
        n.put("monto", t.getMonto() == null ? BigDecimal.ZERO : t.getMonto());
        byte[] cuerpo = json.writeValueAsBytes(n);
        try {
            receptor.recibir(cuerpo, firma.firmar(cuerpo));
        } catch (NegocioException | org.springframework.dao.DataAccessException e) {
            // El resultado (ERROR o el estado previo) se ve en la página de retorno
        }
        URI destino = UriComponentsBuilder.fromUriString(portal + "/pago/resultado")
                .queryParam("referencia", t.getReferencia()).build().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, destino.toString()).build();
    }
}
