package co.gov.epq.sigca.common.validation;

import java.util.regex.Pattern;

/**
 * Reglas de validación de documentos colombianos, reutilizables por validadores y servicios.
 */
public final class DocumentoIdentidad {

    private static final int[] PESOS_NIT = { 3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71 };

    private DocumentoIdentidad() {
    }

    public static boolean esValido(TipoIdentificacion tipo, String numero) {
        if (tipo == null || numero == null) {
            return false;
        }
        if (!Pattern.matches(tipo.patron(), numero)) {
            return false;
        }
        if (tipo == TipoIdentificacion.NIT) {
            String[] partes = numero.split("-");
            return digitoVerificacionNit(partes[0]) == Integer.parseInt(partes[1]);
        }
        return true;
    }

    /**
     * Calcula el dígito de verificación del NIT con el algoritmo de módulo 11 de la DIAN.
     */
    public static int digitoVerificacionNit(String nit) {
        int suma = 0;
        int len = nit.length();
        for (int i = 0; i < len; i++) {
            int digito = Character.digit(nit.charAt(len - 1 - i), 10);
            suma += digito * PESOS_NIT[i];
        }
        int residuo = suma % 11;
        return residuo > 1 ? 11 - residuo : residuo;
    }
}
