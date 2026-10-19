package co.gov.epq.sigca.usuarios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;

/**
 * RNEC simulada para desarrollo y pruebas ({@code sigca.integraciones.rnec.modo=simulado}).
 * Convención: los números que terminan en "0000" no coinciden y los que terminan en "9999"
 * simulan que el servicio no responde.
 */
@Component
@ConditionalOnProperty(name = "sigca.integraciones.rnec.modo", havingValue = "simulado", matchIfMissing = true)
public class ValidadorRnecSimulado implements ValidadorRnec {

    private static final Logger log = LoggerFactory.getLogger(ValidadorRnecSimulado.class);

    @Override
    public Resultado verificar(TipoIdentificacion tipo, String numero, String nombre) {
        log.debug("RNEC simulada: verificando {} {}", tipo, CifradorIdentificacion.mascara(numero));
        if (numero.endsWith("9999")) {
            throw new RnecNoDisponibleException("RNEC simulada no disponible", null);
        }
        return numero.endsWith("0000") ? Resultado.NO_COINCIDE : Resultado.COINCIDE;
    }
}
