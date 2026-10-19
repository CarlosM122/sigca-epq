package co.gov.epq.sigca.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Valores aleatorios de seguridad: enlaces de un solo uso (solo se guarda su SHA-256) y contraseñas temporales
 * que cumplen {@code @PasswordSegura}.
 */
public final class Secretos {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final String SIMBOLOS = "!#$%*+-.:=?@_";

    private Secretos() {
    }

    /** 32 bytes aleatorios en Base64 URL sin relleno (43 caracteres), aptos para ir en un enlace. */
    public static String nuevoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hex minúsculas, el formato de TOKEN_ACCESO.TOKEN_HASH. */
    public static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Contraseña temporal de {@code longitud} caracteres con al menos una mayúscula, minúscula, dígito y símbolo. */
    public static String passwordTemporal(int longitud) {
        int n = Math.max(12, Math.min(longitud, 64));
        List<Character> c = new ArrayList<>(n);
        c.add(azar(MAYUSCULAS));
        c.add(azar(MINUSCULAS));
        c.add(azar(DIGITOS));
        c.add(azar(SIMBOLOS));
        String todos = MAYUSCULAS + MINUSCULAS + DIGITOS + SIMBOLOS;
        while (c.size() < n) {
            c.add(azar(todos));
        }
        Collections.shuffle(c, RANDOM);
        StringBuilder sb = new StringBuilder(n);
        c.forEach(sb::append);
        return sb.toString();
    }

    private static char azar(String alfabeto) {
        return alfabeto.charAt(RANDOM.nextInt(alfabeto.length()));
    }
}
