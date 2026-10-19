package co.gov.epq.sigca.pqr;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import co.gov.epq.sigca.common.validation.DocumentoIdentidad;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Reglas de radicación que dependen del tipo (P-14, P-15, RN-17): QUEJA, RECLAMO y RECURSO exigen contrato;
 * sin contrato (solo PETICION) se exigen la identificación del solicitante y el municipio; el RECURSO señala la
 * PQR que recurre; la factura reclamada solo aplica al RECLAMO y la apelación solo al RECURSO.
 * Cada error se reporta en el campo afectado para que el formulario lo muestre junto al control.
 */
@Documented
@Constraint(validatedBy = RadicacionValida.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RadicacionValida {

    String message() default "Datos de radicación incompletos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<RadicacionValida, PqrSolicitudes.Radicar> {

        @Override
        public boolean isValid(PqrSolicitudes.Radicar r, ConstraintValidatorContext ctx) {
            if (r == null || r.tipo() == null) {
                return true;
            }
            ctx.disableDefaultConstraintViolation();
            boolean ok = true;
            boolean conContrato = r.numeroContrato() != null && !r.numeroContrato().isBlank();
            if (!conContrato && r.tipo() != TipoPqr.PETICION) {
                ok = error(ctx, "numeroContrato", "Una " + r.tipo().name().toLowerCase() + " exige el número de contrato");
            }
            if (!conContrato && r.municipioId() == null) {
                ok = error(ctx, "municipioId", "Indique el municipio de la solicitud");
            }
            boolean conIdentificacion = r.numeroIdentificacion() != null && !r.numeroIdentificacion().isBlank();
            if (!conContrato && (r.tipoIdentificacion() == null || !conIdentificacion)) {
                ok = error(ctx, "numeroIdentificacion", "Sin contrato debe indicar su tipo y número de identificación");
            }
            if (conIdentificacion && r.tipoIdentificacion() != null && !DocumentoIdentidad.esValido(
                    r.tipoIdentificacion(), Normalizador.documento(r.numeroIdentificacion()))) {
                ok = error(ctx, "numeroIdentificacion", r.tipoIdentificacion() == TipoIdentificacion.NIT
                        ? "NIT inválido: use el formato 900123456-7 con dígito de verificación correcto"
                        : "El número no corresponde al formato de " + r.tipoIdentificacion());
            }
            boolean conOriginal = r.radicadoOriginal() != null && !r.radicadoOriginal().isBlank();
            if (r.tipo() == TipoPqr.RECURSO && !conOriginal) {
                ok = error(ctx, "radicadoOriginal", "Indique el radicado de la PQR cuya respuesta recurre");
            }
            if (r.tipo() != TipoPqr.RECURSO && (conOriginal || Boolean.TRUE.equals(r.solicitaApelacion()))) {
                ok = error(ctx, "radicadoOriginal", "Solo un recurso se vincula a otra PQR o pide apelación");
            }
            if (r.numeroFactura() != null && !r.numeroFactura().isBlank() && r.tipo() != TipoPqr.RECLAMO) {
                ok = error(ctx, "numeroFactura", "Solo un reclamo señala una factura");
            }
            return ok;
        }

        private static boolean error(ConstraintValidatorContext ctx, String campo, String mensaje) {
            ctx.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
            return false;
        }
    }
}
