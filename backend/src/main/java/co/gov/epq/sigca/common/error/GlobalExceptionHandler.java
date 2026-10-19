package co.gov.epq.sigca.common.error;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;

/**
 * Traduce todas las excepciones a {@link ApiError}. Nunca expone trazas ni detalles internos al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NegocioException.class)
    ResponseEntity<ApiError> negocio(NegocioException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCodigo(), ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.CampoInvalido> errores = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e instanceof FieldError fe
                        ? new ApiError.CampoInvalido(fe.getField(), fe.getDefaultMessage(), seguro(fe.getField(), fe.getRejectedValue()))
                        : new ApiError.CampoInvalido(e.getObjectName(), e.getDefaultMessage(), null))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "La solicitud contiene datos inválidos", req, errores);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> validacionParametros(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ApiError.CampoInvalido> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ApiError.CampoInvalido(r.getMethodParameter().getParameterName(), e.getDefaultMessage(), null)))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "La solicitud contiene parámetros inválidos", req, errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> restricciones(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.CampoInvalido> errores = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.CampoInvalido(v.getPropertyPath().toString(), v.getMessage(), null))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDACION", "La solicitud contiene datos inválidos", req, errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> ilegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "CUERPO_INVALIDO",
                "El cuerpo de la solicitud no es válido o contiene valores no permitidos", req, List.of());
    }

    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class })
    ResponseEntity<ApiError> parametro(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parámetro inválido o faltante", req, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> archivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamaño permitido", req, List.of());
    }

    @ExceptionHandler({ BadCredentialsException.class })
    ResponseEntity<ApiError> credenciales(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos", req, List.of());
    }

    @ExceptionHandler(LockedException.class)
    ResponseEntity<ApiError> bloqueada(LockedException ex, HttpServletRequest req) {
        return build(HttpStatus.LOCKED, "CUENTA_BLOQUEADA", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<ApiError> inactiva(DisabledException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "CUENTA_INACTIVA", "La cuenta está inactiva", req, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denegado(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "No tiene permisos para realizar esta operación", req, List.of());
    }

    ResponseEntity<ApiError> concurrencia(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "MODIFICACION_CONCURRENTE",
                "El registro fue modificado por otro usuario. Recargue e intente de nuevo", req, List.of());
    }

    /**
     * Errores de BD: primero se intenta traducir las reglas de los triggers y restricciones de Oracle.
     * Las restricciones diferidas (CK_FACTURA_COMPLETA, CK_ACUERDO_CUADRE) fallan al confirmar, por eso
     * también se revisa TransactionSystemException.
     */
    @ExceptionHandler({ DataAccessException.class, PersistenceException.class, TransactionSystemException.class })
    ResponseEntity<ApiError> baseDatos(Exception ex, HttpServletRequest req) {
        var traducido = TraductorErroresOracle.traducir(ex);
        if (traducido.isPresent()) {
            NegocioException n = traducido.get();
            log.info("Regla de BD en {}: {} {}", req.getRequestURI(), n.getCodigo(), n.getMessage());
            return build(n.getStatus(), n.getCodigo(), n.getMessage(), req, List.of());
        }
        if (ex instanceof OptimisticLockingFailureException || ex instanceof OptimisticLockException) {
            return concurrencia(ex, req);
        }
        if (ex instanceof PessimisticLockingFailureException) {
            return build(HttpStatus.CONFLICT, "RECURSO_OCUPADO",
                    "Otro proceso está modificando el mismo registro. Intente de nuevo", req, List.of());
        }
        if (ex instanceof DataIntegrityViolationException dive) {
            return integridad(dive, req);
        }
        return inesperado(ex, req);
    }

    ResponseEntity<ApiError> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "INTEGRIDAD_DATOS", "La operación viola una restricción de integridad de datos", req, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> tipoContenido(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_CONTENIDO_NO_SOPORTADO", "Tipo de contenido no soportado", req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> metodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noEncontrado(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA", "Recurso no encontrado", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Si persiste, contacte a soporte", req, List.of());
    }

    private static Object seguro(String campo, Object valor) {
        String c = campo.toLowerCase();
        if (c.contains("password") || c.contains("contrasena") || c.contains("tarjeta") || c.contains("cvv")) {
            return null;
        }
        return valor;
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String codigo, String mensaje,
            HttpServletRequest req, List<ApiError.CampoInvalido> errores) {
        return ResponseEntity.status(status)
                .body(new ApiError(LocalDateTime.now(), status.value(), codigo, mensaje, req.getRequestURI(), errores));
    }
}
