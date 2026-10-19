package co.gov.epq.sigca.pagos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Pasarela simulada (P-18, P-19): su página de pago la sirve {@link PasarelaSimuladaController}. */
@Component
@ConditionalOnProperty(name = "sigca.integraciones.pasarela.modo", havingValue = "simulado", matchIfMissing = true)
public class PasarelaSimulada implements PasarelaPagos {

    private final PasarelaProperties props;

    public PasarelaSimulada(PasarelaProperties props) {
        this.props = props;
    }

    @Override
    public String urlCheckout(TransaccionPasarela t) {
        String base = props.urlCheckout().endsWith("/") ? props.urlCheckout() : props.urlCheckout() + "/";
        return base + t.getReferencia();
    }
}
