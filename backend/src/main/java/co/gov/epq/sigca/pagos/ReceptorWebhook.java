package co.gov.epq.sigca.pagos;

import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.TraductorErroresOracle;
import co.gov.epq.sigca.pagos.PagoEnLineaService.ResultadoWebhook;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recibe el webhook de la pasarela (P-18): verifica la firma HMAC-SHA256 del cuerpo crudo antes de leerlo
 * (firma inválida → 401 sin tocar nada), valida el contenido y lo aplica. Si la pasarela aprobó pero la BD
 * rechaza el pago, el intento queda en ERROR para su reverso.
 */
@Component
public class ReceptorWebhook {

    private final FirmaWebhook firma;
    private final PagoEnLineaService servicio;
    private final JsonMapper json;
    private final Validator validador;

    public ReceptorWebhook(FirmaWebhook firma, PagoEnLineaService servicio, JsonMapper json, Validator validador) {
        this.firma = firma;
        this.servicio = servicio;
        this.json = json;
        this.validador = validador;
    }

    public ResultadoWebhook recibir(byte[] cuerpo, String firmaRecibida) {
        if (!firma.valida(cuerpo, firmaRecibida)) {
            throw new NegocioException("FIRMA_INVALIDA", "Firma del webhook inválida", HttpStatus.UNAUTHORIZED);
        }
        NotificacionPasarela n;
        try {
            n = json.readValue(cuerpo, NotificacionPasarela.class);
        } catch (JacksonException e) {
            throw new NegocioException("CUERPO_INVALIDO", "El cuerpo del webhook no es válido", HttpStatus.BAD_REQUEST);
        }
        Set<ConstraintViolation<NotificacionPasarela>> errores = validador.validate(n);
        if (!errores.isEmpty()) {
            ConstraintViolation<NotificacionPasarela> v = errores.iterator().next();
            throw new NegocioException("CUERPO_INVALIDO", "Campo " + v.getPropertyPath() + " inválido",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            return servicio.procesar(n);
        } catch (DataAccessException | PersistenceException e) {
            if ("APROBADA".equals(n.estado())) {
                String motivo = TraductorErroresOracle.traducir(e).map(NegocioException::getMessage)
                        .orElse(e.getClass().getSimpleName());
                servicio.marcarNoAplicable(n.referencia(), motivo);
            }
            throw e;
        } catch (NegocioException e) {
            if ("APROBADA".equals(n.estado()) && e.getStatus() == HttpStatus.UNPROCESSABLE_CONTENT) {
                servicio.marcarNoAplicable(n.referencia(), e.getMessage());
            }
            throw e;
        }
    }
}
