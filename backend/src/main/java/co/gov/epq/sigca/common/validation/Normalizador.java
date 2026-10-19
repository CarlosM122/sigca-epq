package co.gov.epq.sigca.common.validation;

import java.util.Locale;

/**
 * Normalización de entradas antes de persistir: recorta espacios, colapsa espacios internos y unifica mayúsculas.
 */
public final class Normalizador {

    private Normalizador() {
    }

    public static String texto(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    public static String correo(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    public static String telefono(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.startsWith("+57") ? t.substring(3) : t;
    }

    public static String documento(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    /** Texto libre sin marcado HTML (prevención de XSS almacenado). */
    public static boolean sinHtml(String s) {
        return s == null || !s.matches("(?s).*<\\s*[a-zA-Z/!].*");
    }
}
