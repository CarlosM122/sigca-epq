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
 * Valida a nivel de clase que el número coincida con el formato del tipo de documento,
 * que el dígito de verificación del NIT sea correcto y que NIT y persona jurídica vayan juntos (RN-09, 7.1).
 */
@Documented
@Constraint(validatedBy = DocumentoValido.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DocumentoValido {

    String message() default "El número de identificación no es válido para el tipo de documento";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<DocumentoValido, ConDocumento> {

        @Override
        public boolean isValid(ConDocumento value, ConstraintValidatorContext ctx) {
            if (value == null || value.tipoIdentificacion() == null || value.numeroIdentificacion() == null) {
                return true; // @NotNull en los campos reporta la ausencia
            }
            ctx.disableDefaultConstraintViolation();
            if (value.tipoPersona() != null
                    && (value.tipoPersona() == TipoPersona.JURIDICA) != (value.tipoIdentificacion() == TipoIdentificacion.NIT)) {
                ctx.buildConstraintViolationWithTemplate("El NIT corresponde a personas jurídicas y una persona jurídica se identifica con NIT")
                        .addPropertyNode("tipoIdentificacion").addConstraintViolation();
                return false;
            }
            if (!DocumentoIdentidad.esValido(value.tipoIdentificacion(), value.numeroIdentificacion())) {
                String msg = value.tipoIdentificacion() == TipoIdentificacion.NIT
                        ? "NIT inválido: use el formato 900123456-7 con dígito de verificación correcto"
                        : "El número no corresponde al formato de " + value.tipoIdentificacion();
                ctx.buildConstraintViolationWithTemplate(msg)
                        .addPropertyNode("numeroIdentificacion").addConstraintViolation();
                return false;
            }
            return true;
        }
    }
}
