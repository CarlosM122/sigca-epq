package co.gov.epq.sigca.seguridad;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * API sin estado protegida con JWT (HS256). Los endpoints públicos son solo los del portal ciudadano
 * que el SyRS declara sin sesión (RF-10) y los de autenticación. Cada endpoint exige su permiso con
 * {@code @PreAuthorize}; la matriz rol-permiso vive en ROL_PERMISO (P-16).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, ManejadorErroresSeguridad errores) throws Exception {
        http
                .csrf(c -> c.disable()) // API sin cookies de sesión: el token viaja en el encabezado Authorization
                .cors(c -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/login-ciudadano",
                                "/api/auth/doble-factor/enrolar", "/api/auth/activacion/**", "/api/auth/recuperacion/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/publico/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/publico/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        // Con la contraseña temporal pendiente de cambio solo se permite /api/auth/**
                        .requestMatchers("/api/**").access(SecurityConfig::sinCambioPasswordPendiente)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errores))
                .exceptionHandling(e -> e.authenticationEntryPoint(errores).accessDeniedHandler(errores));
        return http.build();
    }

    static AuthorizationDecision sinCambioPasswordPendiente(Supplier<? extends Authentication> autenticacion,
            RequestAuthorizationContext contexto) {
        Authentication a = autenticacion.get();
        boolean ok = a != null && a.isAuthenticated() && a.getPrincipal() instanceof Jwt jwt
                && !Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtService.CAMBIO_PASSWORD));
        return new AuthorizationDecision(ok);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Las autoridades son los códigos de PERMISO: @PreAuthorize("hasAuthority('USUARIO_CREAR')")
        JwtGrantedAuthoritiesConverter permisos = new JwtGrantedAuthoritiesConverter();
        permisos.setAuthoritiesClaimName(JwtService.PERMISOS);
        permisos.setAuthorityPrefix("");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(permisos);
        return conv;
    }

    @Bean
    SecretKey jwtKey(SeguridadProperties props) {
        return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SeguridadProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.corsOrigenes());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
