package co.gov.epq.sigca.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ControlIntentosTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 14, 10, 0);

    @Test
    void cuentaLosFallosAntesDelMaximo() {
        ControlIntentos.Resultado r = ControlIntentos.fallo(1, 3, 15, AHORA);
        assertThat(r.intentos()).isEqualTo(2);
        assertThat(r.bloqueadaHasta()).isNull();
        assertThat(r.bloqueoNuevo()).isFalse();
    }

    @Test
    void alLlegarAlMaximoBloqueaYReiniciaElContador() {
        ControlIntentos.Resultado r = ControlIntentos.fallo(2, 3, 15, AHORA);
        assertThat(r.intentos()).isZero();
        assertThat(r.bloqueadaHasta()).isEqualTo(AHORA.plusMinutes(15));
        assertThat(r.bloqueoNuevo()).isTrue();
    }

    @Test
    void contadorNuloEquivaleACero() {
        assertThat(ControlIntentos.fallo(null, 3, 15, AHORA).intentos()).isEqualTo(1);
        assertThat(ControlIntentos.fallo(null, 1, 15, AHORA).bloqueoNuevo()).isTrue();
    }

    @Test
    void elBloqueoVenceAlCumplirseElPlazo() {
        assertThat(ControlIntentos.bloqueada(null, AHORA)).isFalse();
        assertThat(ControlIntentos.bloqueada(AHORA.plusSeconds(1), AHORA)).isTrue();
        assertThat(ControlIntentos.bloqueada(AHORA, AHORA)).isFalse();
    }
}
