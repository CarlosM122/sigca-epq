package co.gov.epq.sigca.common.cifrado;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import co.gov.epq.sigca.common.validation.TipoIdentificacion;

/**
 * Protege el número de identificación según el modelo Oracle:
 * <ul>
 * <li>{@code NUMERO_IDENTIFICACION_CIFRADO}: AES-256-GCM, IV aleatorio de 12 bytes antepuesto, en Base64.</li>
 * <li>{@code NUMERO_IDENTIFICACION_HASH}: HMAC-SHA256 en hex (índice ciego) sobre "TIPO:NUMERO", para unicidad y búsqueda exacta.</li>
 * <li>{@code NUMERO_IDENTIFICACION_MASCARA}: asteriscos más los últimos 4 caracteres.</li>
 * </ul>
 */
@Component
public class CifradorIdentificacion {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec claveAes;
    private final SecretKeySpec claveHmac;
    private final SecureRandom random = new SecureRandom();

    public CifradorIdentificacion(CifradoProperties props) {
        byte[] aes = Base64.getDecoder().decode(props.claveAes());
        byte[] hmac = Base64.getDecoder().decode(props.claveHmac());
        if (aes.length != 32 || hmac.length != 32) {
            throw new IllegalStateException("sigca.cifrado: las claves deben ser de 32 bytes en Base64");
        }
        this.claveAes = new SecretKeySpec(aes, "AES");
        this.claveHmac = new SecretKeySpec(hmac, "HmacSHA256");
    }

    public String cifrar(String numero) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claveAes, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(numero.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No fue posible cifrar la identificación", e);
        }
    }

    public String descifrar(String base64) {
        try {
            byte[] datos = Base64.getDecoder().decode(base64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, claveAes, new GCMParameterSpec(TAG_BITS, datos, 0, IV_BYTES));
            return new String(cipher.doFinal(datos, IV_BYTES, datos.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No fue posible descifrar la identificación", e);
        }
    }

    public String hash(TipoIdentificacion tipo, String numero) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveHmac);
            return HexFormat.of().formatHex(mac.doFinal((tipo.name() + ":" + numero).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No fue posible calcular el hash de la identificación", e);
        }
    }

    public static String mascara(String numero) {
        int visibles = Math.min(4, Math.max(1, numero.length() - 2));
        return "*".repeat(Math.max(1, numero.length() - visibles)) + numero.substring(numero.length() - visibles);
    }
}
