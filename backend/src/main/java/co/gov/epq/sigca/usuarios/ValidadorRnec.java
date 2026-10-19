package co.gov.epq.sigca.usuarios;

import co.gov.epq.sigca.common.validation.TipoIdentificacion;

/**
 * Puerto hacia la Registraduría (IS-03, P-19). La implementación real se conecta por SOAP/REST;
 * en desarrollo se usa {@link ValidadorRnecSimulado}.
 */
public interface ValidadorRnec {

    Resultado verificar(TipoIdentificacion tipo, String numero, String nombre);

    enum Resultado {
        COINCIDE, NO_COINCIDE
    }

    /** La RNEC no respondió: el registro continúa con el usuario PENDIENTE de validación (CU-01 4b, RN-11). */
    class RnecNoDisponibleException extends RuntimeException {

        public RnecNoDisponibleException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
