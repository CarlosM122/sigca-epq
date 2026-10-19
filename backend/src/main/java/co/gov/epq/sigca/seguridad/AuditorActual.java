package co.gov.epq.sigca.seguridad;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component("auditorActual")
public class AuditorActual implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(UsuarioActual.usernameOSistema());
    }
}
