package co.gov.epq.sigca.tarifas;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.tarifas.TarifaDtos.TarifaDatos;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** CK_TARIFA_RANGOS: rangos obligatorios para acueducto y alcantarillado, prohibidos para gas. */
@Documented
@Constraint(validatedBy = RangosTarifa.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RangosTarifa {

    String message() default "Rangos de consumo inválidos para el servicio";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<RangosTarifa, TarifaDatos> {

        @Override
        public boolean isValid(TarifaDatos t, ConstraintValidatorContext ctx) {
            if (t == null || t.tipoServicio() == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            boolean ok = true;
            if (t.tipoServicio() == TipoServicio.GAS) {
                ok &= nulo(ctx, "valorM3Complementario", t.valorM3Complementario());
                ok &= nulo(ctx, "valorM3Suntuario", t.valorM3Suntuario());
                ok &= nulo(ctx, "consumoBasicoM3", t.consumoBasicoM3());
                ok &= nulo(ctx, "consumoSuntuarioDesdeM3", t.consumoSuntuarioDesdeM3());
                return ok;
            }
            ok &= presente(ctx, "valorM3Complementario", t.valorM3Complementario());
            ok &= presente(ctx, "valorM3Suntuario", t.valorM3Suntuario());
            ok &= presente(ctx, "consumoBasicoM3", t.consumoBasicoM3());
            ok &= presente(ctx, "consumoSuntuarioDesdeM3", t.consumoSuntuarioDesdeM3());
            if (ok && t.consumoSuntuarioDesdeM3().compareTo(t.consumoBasicoM3()) < 0) {
                ctx.buildConstraintViolationWithTemplate("El consumo suntuario empieza en o después del consumo básico")
                        .addPropertyNode("consumoSuntuarioDesdeM3").addConstraintViolation();
                ok = false;
            }
            return ok;
        }

        private static boolean nulo(ConstraintValidatorContext ctx, String campo, Object v) {
            if (v == null) {
                return true;
            }
            ctx.buildConstraintViolationWithTemplate("El gas (CREG) no tiene rangos de consumo: deje este campo vacío")
                    .addPropertyNode(campo).addConstraintViolation();
            return false;
        }

        private static boolean presente(ConstraintValidatorContext ctx, String campo, Object v) {
            if (v != null) {
                return true;
            }
            ctx.buildConstraintViolationWithTemplate("Obligatorio para acueducto y alcantarillado (metodología CRA)")
                    .addPropertyNode(campo).addConstraintViolation();
            return false;
        }
    }
}
