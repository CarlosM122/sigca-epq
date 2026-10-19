package co.gov.epq.sigca.lecturas;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

import co.gov.epq.sigca.lecturas.LecturaDtos.LecturaRegistrar;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Reglas baratas de una lectura antes de la BD: periodo AAAAMM válido, fecha dentro del mes del periodo o el
 * siguiente (TRG_LECTURA_CIUD), lectura o causa (CK_LECTURA_TOMADA) y observación si la causa es OTRA.
 */
@Documented
@Constraint(validatedBy = LecturaCoherente.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LecturaCoherente {

    String message() default "Lectura inválida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<LecturaCoherente, LecturaRegistrar> {

        @Override
        public boolean isValid(LecturaRegistrar l, ConstraintValidatorContext ctx) {
            if (l == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            boolean ok = true;
            if (l.periodo() != null) {
                int mes = l.periodo() % 100;
                if (l.periodo() < 200001 || l.periodo() > 209912 || mes < 1 || mes > 12) {
                    ok = error(ctx, "periodo", "El periodo debe tener formato AAAAMM");
                } else if (l.fechaLectura() != null) {
                    LocalDate inicio = LocalDate.of(l.periodo() / 100, mes, 1);
                    LocalDate fin = inicio.plusMonths(2).minusDays(1);
                    if (l.fechaLectura().isBefore(inicio) || l.fechaLectura().isAfter(fin)) {
                        ok = error(ctx, "fechaLectura", "La fecha debe estar en el mes del periodo o en el siguiente");
                    }
                }
            }
            if (l.lecturaActual() == null && l.causaNoLectura() == null) {
                ok = error(ctx, "lecturaActual", "Indique la lectura o la causa de no lectura");
            } else if (l.lecturaActual() != null && l.causaNoLectura() != null) {
                ok = error(ctx, "causaNoLectura", "Si hay lectura no se indica causa de no lectura");
            }
            if (l.causaNoLectura() == CausaNoLectura.OTRA && (l.observacion() == null || l.observacion().isBlank())) {
                ok = error(ctx, "observacion", "Describa la causa en la observación");
            }
            return ok;
        }

        private static boolean error(ConstraintValidatorContext ctx, String campo, String mensaje) {
            ctx.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
            return false;
        }
    }
}
