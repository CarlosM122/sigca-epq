package co.gov.epq.sigca.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** El teléfono inválido se reporta con el mensaje propio de la anotación, no con el del @Pattern interno. */
class TelefonoColombiaTest {

    record Contacto(@TelefonoColombia String telefono) {
    }

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void iniciar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    @Test
    void aceptaCelularFijoYPrefijo() {
        assertThat(validador.validate(new Contacto("3104567890"))).isEmpty();
        assertThat(validador.validate(new Contacto("6067491234"))).isEmpty();
        assertThat(validador.validate(new Contacto("+573104567890"))).isEmpty();
    }

    @Test
    void rechazaConUnSoloMensajeEnEspanol() {
        Set<ConstraintViolation<Contacto>> v = validador.validate(new Contacto("12345"));
        assertThat(v).hasSize(1);
        assertThat(v.iterator().next().getMessage()).startsWith("Teléfono inválido");
    }
}
