package co.gov.epq.sigca.seguridad;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import co.gov.epq.sigca.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Límite de solicitudes por IP en los endpoints sin sesión (login, enlaces, radicación y consulta pública
 * de PQR): ventana fija de un minuto. Frena fuerza bruta y abuso; el bloqueo por cuenta lo hace el login.
 * En un despliegue con varias instancias, sustituir por un límite en el balanceador o un almacén compartido.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimiteSolicitudes extends OncePerRequestFilter {

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final JsonMapper mapper;
    private final int maximoPorMinuto;

    public LimiteSolicitudes(JsonMapper mapper, @Value("${sigca.seguridad.limite-por-minuto:20}") int maximoPorMinuto) {
        this.mapper = mapper;
        this.maximoPorMinuto = maximoPorMinuto;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return !"POST".equals(req.getMethod())
                || !(uri.startsWith("/api/auth/") || uri.startsWith("/api/publico/pqr"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long minuto = System.currentTimeMillis() / 60_000;
        String clave = req.getRemoteAddr() + "|" + grupo(req.getRequestURI());
        Ventana v = ventanas.compute(clave, (k, actual) -> actual == null || actual.minuto != minuto ? new Ventana(minuto) : actual);
        if (v.contador.incrementAndGet() > maximoPorMinuto) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", "60");
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            mapper.writeValue(res.getOutputStream(), new ApiError(LocalDateTime.now(), 429, "DEMASIADAS_SOLICITUDES",
                    "Demasiadas solicitudes. Espere un minuto e intente de nuevo", req.getRequestURI(), List.of()));
            return;
        }
        if (ventanas.size() > 10_000) {
            ventanas.values().removeIf(x -> x.minuto != minuto);
        }
        chain.doFilter(req, res);
    }

    private static String grupo(String uri) {
        return uri.startsWith("/api/auth/") ? "auth" : "pqr";
    }

    private static final class Ventana {
        final long minuto;
        final AtomicInteger contador = new AtomicInteger();

        Ventana(long minuto) {
            this.minuto = minuto;
        }
    }
}
