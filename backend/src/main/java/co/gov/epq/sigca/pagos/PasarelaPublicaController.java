package co.gov.epq.sigca.pagos;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.pagos.PagoEnLineaService.ResultadoWebhook;
import jakarta.validation.constraints.Pattern;

/**
 * Endpoints públicos del pago en línea: el webhook de la pasarela (autenticado por firma HMAC, no por sesión) y la
 * consulta del resultado por la referencia aleatoria del intento, que usa la página de retorno.
 */
@RestController
@RequestMapping("/api/publico")
@Validated
public class PasarelaPublicaController {

    public static final String ENCABEZADO_FIRMA = "X-Firma-Pasarela";

    private final ReceptorWebhook receptor;
    private final PagoEnLineaService servicio;

    public PasarelaPublicaController(ReceptorWebhook receptor, PagoEnLineaService servicio) {
        this.receptor = receptor;
        this.servicio = servicio;
    }

    @PostMapping(value = "/pasarela/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResultadoWebhook webhook(@RequestBody byte[] cuerpo,
            @RequestHeader(name = ENCABEZADO_FIRMA, required = false) String firma) {
        return receptor.recibir(cuerpo, firma);
    }

    @GetMapping("/pagos/{referencia}")
    public EstadoTransaccionVista estado(
            @PathVariable @Pattern(regexp = "^TX-[0-9A-F]{32}$", message = "Referencia inválida") String referencia) {
        return servicio.estado(referencia);
    }
}
