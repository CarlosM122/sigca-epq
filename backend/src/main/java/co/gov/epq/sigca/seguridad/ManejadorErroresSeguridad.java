package co.gov.epq.sigca.seguridad;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import co.gov.epq.sigca.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responde 401/403 con el mismo formato {@link ApiError} que el resto de la API.
 */
@Component
public class ManejadorErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper mapper;

    public ManejadorErroresSeguridad(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        escribir(req, res, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Debe iniciar sesión o su sesión expiró");
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException {
        escribir(req, res, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tiene permisos para realizar esta operación");
    }

    private void escribir(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String codigo, String mensaje)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(),
                new ApiError(LocalDateTime.now(), status.value(), codigo, mensaje, req.getRequestURI(), List.of()));
    }
}
