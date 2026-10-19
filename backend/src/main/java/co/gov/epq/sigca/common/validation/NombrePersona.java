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
import jakarta.validation.constraints.Size;

/**
 * Nombre o razón social: letras (incluye tildes y ñ), dígitos, espacios y . , ' - &amp;. Entre 3 y 150 caracteres.
 */
@Documented
@Size(min = 3, max = 150)
@Pattern(regexp = "^[\\p{L}0-9][\\p{L}0-9 .,'&-]*[\\p{L}0-9.]$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface NombrePersona {

    String message() default "Nombre inválido: entre 3 y 150 caracteres, solo letras, números y . , ' - &";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
