package co.gov.epq.sigca.usuarios;

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
 * CK_PREDIO_ESTRATO / CK_FACTOR_ESTRATO: el estrato (1 a 6) es obligatorio para el uso residencial y
 * no aplica a los demás usos. El error se reporta en el campo {@code estrato}.
 */
@Documented
@Constraint(validatedBy = EstratoSegunUso.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EstratoSegunUso {

    String message() default "Estrato inválido para el uso";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    interface ConEstrato {
        UsoPredio uso();

        Integer estrato();
    }

    class Validador implements ConstraintValidator<EstratoSegunUso, ConEstrato> {

        @Override
        public boolean isValid(ConEstrato v, ConstraintValidatorContext ctx) {
            if (v == null || v.uso() == null) {
                return true;
            }
            String error = null;
            if (v.uso() == UsoPredio.RESIDENCIAL && v.estrato() == null) {
                error = "El estrato es obligatorio para el uso residencial";
            } else if (v.uso() == UsoPredio.RESIDENCIAL && (v.estrato() < 1 || v.estrato() > 6)) {
                error = "El estrato va de 1 a 6";
            } else if (v.uso() != UsoPredio.RESIDENCIAL && v.estrato() != null) {
                error = "El estrato solo aplica al uso residencial";
            }
            if (error == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(error).addPropertyNode("estrato").addConstraintViolation();
            return false;
        }
    }
}
