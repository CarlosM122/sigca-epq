package co.gov.epq.sigca.notificaciones;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Integraciones simuladas: escriben en el log en lugar de enviar. Con DEBUG (perfil dev) el correo simulado también
 * escribe los enlaces del mensaje (activación, recuperación), sin el resto del cuerpo, que tiene datos personales.
 * Para producción se implementan {@link EnviadorCorreo} (JavaMailSender) y {@link EnviadorSms} (operador contratado) y se cambia el modo.
 */
@Configuration
public class EnviadoresSimulados {

    private static final Logger log = LoggerFactory.getLogger(EnviadoresSimulados.class);
    private static final Pattern ENLACE = Pattern.compile("https?://[^\\s]+[^\\s.,;)]");

    @Bean
    @ConditionalOnProperty(name = "sigca.integraciones.correo.modo", havingValue = "simulado", matchIfMissing = true)
    EnviadorCorreo correoSimulado() {
        return (destinatario, asunto, cuerpo) -> {
            log.info("[CORREO SIMULADO] para={} asunto='{}'", enmascararCorreo(destinatario), asunto);
            if (log.isDebugEnabled()) {
                enlaces(cuerpo).forEach(e -> log.debug("[CORREO SIMULADO] enlace: {}", e));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "sigca.integraciones.sms.modo", havingValue = "simulado", matchIfMissing = true)
    EnviadorSms smsSimulado() {
        return (telefono, texto) -> log.info("[SMS SIMULADO] para=******{} ({} caracteres)",
                telefono.substring(Math.max(0, telefono.length() - 4)), texto.length());
    }

    static List<String> enlaces(String cuerpo) {
        return cuerpo == null ? List.of() : ENLACE.matcher(cuerpo).results().map(m -> m.group()).toList();
    }

    /** RNF-20: no se escriben datos personales completos en los logs. */
    static String enmascararCorreo(String correo) {
        int at = correo.indexOf('@');
        if (at <= 1) {
            return "***" + correo.substring(Math.max(at, 0));
        }
        return correo.charAt(0) + "***" + correo.substring(at);
    }
}
