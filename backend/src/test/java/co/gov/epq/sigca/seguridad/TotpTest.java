package co.gov.epq.sigca.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TotpTest {

    /** Vectores del apéndice B de RFC 6238 (SHA1, clave "12345678901234567890"), truncados a 6 dígitos. */
    @Test
    void coincideConLosVectoresDelRfc() {
        byte[] clave = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.codigo(clave, 59L / 30)).isEqualTo("287082");
        assertThat(Totp.codigo(clave, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.codigo(clave, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void verificaConToleranciaDeUnPasoYRechazaFormatosInvalidos() {
        String secreto = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        Clock reloj = Clock.fixed(Instant.ofEpochSecond(59 + 30), ZoneOffset.UTC);
        assertThat(Totp.verificar(secreto, "287082", reloj)).isTrue();
        assertThat(Totp.verificar(secreto, "000000", reloj)).isFalse();
        assertThat(Totp.verificar(secreto, "28708", reloj)).isFalse();
        assertThat(Totp.verificar(secreto, null, reloj)).isFalse();
    }

    @Test
    void base32IdaYVuelta() {
        String s = Totp.nuevoSecreto();
        assertThat(Totp.base32(Totp.decodificarBase32(s))).isEqualTo(s);
    }
}
