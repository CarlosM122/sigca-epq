package co.gov.epq.sigca.seguridad;

import org.springframework.stereotype.Component;

import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.NegocioException;

/**
 * Reglas de contraseña que no cubre {@code @PasswordSegura}: longitud mínima parametrizable
 * (PASSWORD_MIN_LONGITUD), confirmación, distinta de la actual y, en el portal, distinta del número de
 * identificación (7.10).
 */
@Component
public class PoliticaPassword {

    private final Parametros parametros;

    public PoliticaPassword(Parametros parametros) {
        this.parametros = parametros;
    }

    public void validar(String nueva, String confirmacion) {
        if (!nueva.equals(confirmacion)) {
            throw new NegocioException("PASSWORD_NO_COINCIDE", "La confirmación no coincide con la nueva contraseña");
        }
        int minimo = parametros.entero(Parametros.PASSWORD_MIN_LONGITUD);
        if (nueva.length() < minimo) {
            throw new NegocioException("PASSWORD_CORTA", "La contraseña debe tener al menos " + minimo + " caracteres");
        }
    }

    public void distintaDeIdentificacion(String nueva, String numeroIdentificacion) {
        if (numeroIdentificacion != null && nueva.toUpperCase().contains(numeroIdentificacion.toUpperCase())) {
            throw new NegocioException("PASSWORD_ES_IDENTIFICACION",
                    "La contraseña no puede contener su número de identificación");
        }
    }
}
