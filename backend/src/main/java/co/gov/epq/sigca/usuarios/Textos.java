package co.gov.epq.sigca.usuarios;

/** Expresiones que replican los CHECK de Oracle para validar con Bean Validation antes de llegar a la BD. */
public final class Textos {

    /** CK_USUARIO_CORREO (se compara en minúsculas). */
    public static final String CORREO = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    /** Texto de 5 a 200 caracteres sin contar espacios en los extremos (LENGTH(TRIM(x)) >= 5). */
    public static final String TEXTO_5_200 = "(?s)^\\s*\\S.{3,198}\\S\\s*$";
    /** Motivos y observaciones: de 5 a 300 caracteres útiles. */
    public static final String TEXTO_5_300 = "(?s)^\\s*\\S.{3,298}\\S\\s*$";
    /** CK_USUARIO_NOMBRE para persona natural. */
    public static final String NOMBRE_NATURAL = "^\\p{L}[\\p{L} .'-]*$";
    /** CK_USUARIO_NOMBRE para persona jurídica (razón social). */
    public static final String RAZON_SOCIAL = "^[\\p{L}\\p{N}][\\p{L}\\p{N} .,&'()/-]*$";
    /** CK_PREDIO_FICHA: número predial nacional IGAC. */
    public static final String FICHA_CATASTRAL = "^\\d{30}$";
    /** CK_MEDIDOR_SERIAL. */
    public static final String SERIAL_MEDIDOR = "^[A-Z0-9-]{4,30}$";

    private Textos() {
    }

    public static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    /** Recorta y devuelve null si queda vacío. */
    public static String opcional(String s) {
        return vacio(s) ? null : s.trim().replaceAll("\\s+", " ");
    }
}
