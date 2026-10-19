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
 * Política de contraseñas (MSPI): 8-64 caracteres, al menos una mayúscula, una minúscula, un dígito y un símbolo.
 */
@Documented
@Size(min = 8, max = 64)
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S+$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordSegura {

    String message() default "La contraseña debe tener entre 8 y 64 caracteres, sin espacios, con mayúscula, minúscula, número y símbolo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
