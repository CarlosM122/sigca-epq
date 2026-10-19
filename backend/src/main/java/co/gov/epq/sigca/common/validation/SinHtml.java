package co.gov.epq.sigca.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Rechaza texto libre que contenga etiquetas HTML.
 */
@Documented
@Constraint(validatedBy = SinHtml.Validador.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SinHtml {

    String message() default "El texto no puede contener etiquetas HTML";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SinHtml, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return Normalizador.sinHtml(value);
        }
    }
}
