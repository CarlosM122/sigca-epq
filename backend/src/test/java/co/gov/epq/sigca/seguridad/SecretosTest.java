package co.gov.epq.sigca.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class SecretosTest {

    private static final String POLITICA = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,64}$";

    @Test
    void tokenDe32BytesEnBase64UrlYUnico() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String t = Secretos.nuevoToken();
            assertThat(t).hasSize(43).matches("^[A-Za-z0-9_-]+$");
            assertThat(vistos.add(t)).isTrue();
        }
    }

    @Test
    void sha256EnHexMinusculas() {
        // Vector conocido de SHA-256("abc")
        assertThat(Secretos.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Secretos.sha256(Secretos.nuevoToken())).matches("^[0-9a-f]{64}$");
    }

    @RepeatedTest(50)
    void passwordTemporalCumpleLaPolitica() {
        assertThat(Secretos.passwordTemporal(16)).hasSize(16).matches(POLITICA);
    }

    @Test
    void passwordTemporalRespetaLimites() {
        assertThat(Secretos.passwordTemporal(4)).hasSize(12);
        assertThat(Secretos.passwordTemporal(100)).hasSize(64);
    }
}
