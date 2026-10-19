package co.gov.epq.sigca.usuarios;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import co.gov.epq.sigca.common.validation.TipoPersona;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * CK_USUARIO_NOMBRE: el nombre de una persona natural solo lleva letras, espacios, punto, apóstrofo y guion;
 * la razón social de una jurídica admite además dígitos y , &amp; ( ) /. El error se reporta en {@code nombreCompleto}.
 */
@Documented
@Constraint(validatedBy = NombreSegunPersona.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NombreSegunPersona {

    String message() default "Nombre inválido para el tipo de persona";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    interface ConNombre {
        TipoPersona tipoPersona();

        String nombreCompleto();
    }

    class Validador implements ConstraintValidator<NombreSegunPersona, ConNombre> {

        @Override
        public boolean isValid(ConNombre v, ConstraintValidatorContext ctx) {
            if (v == null || v.tipoPersona() == null || v.nombreCompleto() == null || v.nombreCompleto().isBlank()) {
                return true;
            }
            String error = errorNombre(v.tipoPersona(), v.nombreCompleto().trim().replaceAll("\\s+", " "));
            if (error == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(error).addPropertyNode("nombreCompleto").addConstraintViolation();
            return false;
        }

        /** Devuelve el mensaje de error o null si el nombre (ya normalizado) es válido. */
        public static String errorNombre(TipoPersona tipo, String nombre) {
            if (tipo == TipoPersona.NATURAL && !nombre.matches(Textos.NOMBRE_NATURAL)) {
                return "El nombre de una persona natural solo admite letras, espacios, punto, apóstrofo y guion";
            }
            if (tipo == TipoPersona.JURIDICA && !nombre.matches(Textos.RAZON_SOCIAL)) {
                return "La razón social solo admite letras, números, espacios y . , & ' ( ) / -";
            }
            return null;
        }
    }
}
