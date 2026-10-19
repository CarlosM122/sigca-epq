package co.gov.epq.sigca.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnviadoresSimuladosTest {

    @Test
    void extraeSoloLosEnlacesDelCuerpo() {
        String cuerpo = "Hola Ana. Para activar su cuenta abra este enlace (válido 24 horas): "
                + "http://localhost:4200/cuenta/activacion?token=V8rM-_23R . Si usted no lo solicitó, ignórelo.";
        assertThat(EnviadoresSimulados.enlaces(cuerpo))
                .containsExactly("http://localhost:4200/cuenta/activacion?token=V8rM-_23R");
        assertThat(EnviadoresSimulados.enlaces("Su factura FAC-202609-0000004 vence el 2026-10-29.")).isEmpty();
        assertThat(EnviadoresSimulados.enlaces(null)).isEmpty();
    }

    @Test
    void enmascaraElCorreo() {
        assertThat(EnviadoresSimulados.enmascararCorreo("maria.lopez@example.com")).isEqualTo("m***@example.com");
    }
}
