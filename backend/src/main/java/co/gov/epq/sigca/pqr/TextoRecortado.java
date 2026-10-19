package co.gov.epq.sigca.pqr;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import co.gov.epq.sigca.common.validation.Normalizador;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Texto libre con longitud medida sin espacios en los extremos (como {@code LENGTH(TRIM(x))} en los CHECK de Oracle)
 * y sin etiquetas HTML. Un valor nulo es válido: la obligatoriedad la pone {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = TextoRecortado.Validador.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface TextoRecortado {

    int min();

    int max();

    String message() default "Debe tener entre {min} y {max} caracteres, sin etiquetas HTML";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<TextoRecortado, String> {

        private int min;
        private int max;

        @Override
        public void initialize(TextoRecortado a) {
            min = a.min();
            max = a.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            if (value == null) {
                return true;
            }
            int largo = value.strip().length();
            return largo >= min && largo <= max && Normalizador.sinHtml(value);
        }
    }
}
