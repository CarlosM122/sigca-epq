package co.gov.epq.sigca.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/**
 * Celular colombiano (10 dígitos que inician en 3) o fijo nacional (10 dígitos que inician en 60),
 * con prefijo +57 opcional. Use {@link Normalizador#telefono(String)} antes de persistir.
 */
@Documented
@Pattern(regexp = "^(\\+57)?(3\\d{9}|60\\d{8})$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface TelefonoColombia {

    String message() default "Teléfono inválido: use 10 dígitos (celular 3XXXXXXXXX o fijo 60XXXXXXXX)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
