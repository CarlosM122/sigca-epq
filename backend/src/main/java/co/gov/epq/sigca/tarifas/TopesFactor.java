package co.gov.epq.sigca.tarifas;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.tarifas.TarifaDtos.FactorDatos;
import co.gov.epq.sigca.usuarios.UsoPredio;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * CK_FACTOR_TOPES (Ley 142 art. 99, Ley 1450 art. 125): subsidio máximo estrato 1 −70 %, estrato 2 −40 %,
 * estrato 3 −15 %; estrato 4 y uso oficial 0 %; estratos 5 y 6, comercial e industrial, contribución de 0 a 100 %.
 * Los factores solo aplican a acueducto y alcantarillado (el gas no tiene subsidios en esta versión).
 */
@Documented
@Constraint(validatedBy = TopesFactor.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TopesFactor {

    String message() default "Porcentaje fuera de los topes legales";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<TopesFactor, FactorDatos> {

        @Override
        public boolean isValid(FactorDatos f, ConstraintValidatorContext ctx) {
            if (f == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            if (f.tipoServicio() == TipoServicio.GAS) {
                ctx.buildConstraintViolationWithTemplate("El gas no tiene subsidios ni contribuciones en esta versión")
                        .addPropertyNode("tipoServicio").addConstraintViolation();
                return false;
            }
            if (f.uso() == null || f.porcentaje() == null || (f.uso() == UsoPredio.RESIDENCIAL && f.estrato() == null)) {
                return true;
            }
            String error = error(f.uso(), f.estrato(), f.porcentaje());
            if (error == null) {
                return true;
            }
            ctx.buildConstraintViolationWithTemplate(error).addPropertyNode("porcentaje").addConstraintViolation();
            return false;
        }

        static String error(UsoPredio uso, Integer estrato, BigDecimal p) {
            if (uso == UsoPredio.OFICIAL) {
                return p.signum() == 0 ? null : "El uso oficial no tiene subsidio ni contribución (0 %)";
            }
            if (uso != UsoPredio.RESIDENCIAL) {
                return entre(p, 0, 100) ? null : "Comercial e industrial: contribución entre 0 y 100 %";
            }
            return switch (estrato) {
                case 1 -> entre(p, -70, 0) ? null : "Estrato 1: subsidio entre −70 % y 0 %";
                case 2 -> entre(p, -40, 0) ? null : "Estrato 2: subsidio entre −40 % y 0 %";
                case 3 -> entre(p, -15, 0) ? null : "Estrato 3: subsidio entre −15 % y 0 %";
                case 4 -> p.signum() == 0 ? null : "Estrato 4: sin subsidio ni contribución (0 %)";
                case 5, 6 -> entre(p, 0, 100) ? null : "Estratos 5 y 6: contribución entre 0 y 100 %";
                default -> null; // el rango de estrato lo reporta @EstratoSegunUso
            };
        }

        private static boolean entre(BigDecimal p, int min, int max) {
            return p.compareTo(BigDecimal.valueOf(min)) >= 0 && p.compareTo(BigDecimal.valueOf(max)) <= 0;
        }
    }
}
