package co.gov.epq.sigca.pagos;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/** Firma HMAC-SHA256 del cuerpo del webhook (P-18). La verificación compara en tiempo constante. */
@Component
public class FirmaWebhook {

    private static final String ALGORITMO = "HmacSHA256";
    private final SecretKeySpec clave;

    public FirmaWebhook(PasarelaProperties props) {
        this.clave = new SecretKeySpec(props.secretoWebhook().getBytes(StandardCharsets.UTF_8), ALGORITMO);
    }

    /** Firma en hexadecimal (minúsculas) del cuerpo tal como llegó. */
    public String firmar(byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(clave);
            return HexFormat.of().formatHex(mac.doFinal(cuerpo));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    public boolean valida(byte[] cuerpo, String firma) {
        if (firma == null || cuerpo == null) {
            return false;
        }
        byte[] esperada = firmar(cuerpo).getBytes(StandardCharsets.US_ASCII);
        byte[] recibida = firma.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperada, recibida);
    }
}
