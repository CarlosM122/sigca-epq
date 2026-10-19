package co.gov.epq.sigca.seguridad;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de un solo uso TOTP (RFC 6238: HMAC-SHA1, 6 dígitos, pasos de 30 s), compatibles con
 * Google Authenticator, Microsoft Authenticator y similares. Doble factor obligatorio para funcionarios (P-20).
 */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PASO_SEGUNDOS = 30;
    private static final int DIGITOS = 6;
    /** Acepta el paso anterior y el siguiente para tolerar relojes desfasados. */
    private static final int VENTANA = 1;

    private Totp() {
    }

    /** Secreto aleatorio de 160 bits en Base32, para registrar en la aplicación autenticadora. */
    public static String nuevoSecreto() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return base32(bytes);
    }

    /** URI otpauth:// que se muestra como código QR al enrolar al funcionario. */
    public static String uri(String emisor, String cuenta, String secreto) {
        String e = java.net.URLEncoder.encode(emisor, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String c = java.net.URLEncoder.encode(cuenta, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + e + ":" + c + "?secret=" + secreto + "&issuer=" + e + "&digits=" + DIGITOS + "&period=" + PASO_SEGUNDOS;
    }

    public static boolean verificar(String secreto, String codigo, Clock reloj) {
        if (codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return false;
        }
        long paso = Instant.now(reloj).getEpochSecond() / PASO_SEGUNDOS;
        byte[] clave = decodificarBase32(secreto);
        boolean valido = false;
        for (int i = -VENTANA; i <= VENTANA; i++) {
            // Comparación en tiempo constante y sin salir antes, para no filtrar información por tiempos.
            valido |= MessageDigest.isEqual(codigo(clave, paso + i).getBytes(), codigo.getBytes());
        }
        return valido;
    }

    static String codigo(byte[] clave, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(clave, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int offset = h[h.length - 1] & 0x0f;
            int binario = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16) | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", binario % (int) Math.pow(10, DIGITOS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] decodificarBase32(String texto) {
        String limpio = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = BASE32.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Secreto TOTP inválido");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return salida.array();
    }
}
