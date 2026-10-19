package co.gov.epq.sigca.common.error;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Formato único de respuesta de error de la API (inspirado en RFC 9457).
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String codigo,
        String mensaje,
        String ruta,
        List<CampoInvalido> errores) {

    public record CampoInvalido(String campo, String mensaje, Object valorRechazado) {
    }
}
