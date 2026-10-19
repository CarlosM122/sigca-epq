package co.gov.epq.sigca.common.error;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

/**
 * Convierte los errores que levantan los triggers de Oracle (RAISE_APPLICATION_ERROR -20001..-20021,
 * ver 03_logica.sql) y las violaciones de UNIQUE/CHECK en {@link NegocioException} con un código estable.
 * Así la regla vive en la BD, pero el cliente recibe el mismo formato de error que el resto de la API.
 */
public final class TraductorErroresOracle {

    private record Regla(String codigo, HttpStatus status) {
    }

    private static final Map<Integer, Regla> APLICACION = Map.ofEntries(
            Map.entry(20001, new Regla("TRANSICION_NO_PERMITIDA", HttpStatus.CONFLICT)),
            Map.entry(20002, new Regla("CONTRATO_NO_VALIDO", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20003, new Regla("MUNICIPIO_NO_ATENDIDO", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20004, new Regla("NO_ADMITE_PAGOS", HttpStatus.CONFLICT)),
            Map.entry(20005, new Regla("VALOR_NO_COINCIDE", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20006, new Regla("REGISTRO_INMUTABLE", HttpStatus.CONFLICT)),
            Map.entry(20007, new Regla("TARIFA_NO_VIGENTE", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20008, new Regla("FECHA_FUTURA", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20009, new Regla("SIN_EVIDENCIA", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20010, new Regla("CONSECUTIVO_AGOTADO", HttpStatus.SERVICE_UNAVAILABLE)),
            Map.entry(20011, new Regla("PARAMETRO_INVALIDO", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20012, new Regla("OBLIGACIONES_PENDIENTES", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20013, new Regla("ACUERDO_INVALIDO", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20014, new Regla("CAMPO_NO_MODIFICABLE", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20015, new Regla("FUNCIONARIO_SIN_ROL", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20016, new Regla("PAGO_SIN_CONFIRMACION", HttpStatus.CONFLICT)),
            Map.entry(20017, new Regla("LECTURA_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20018, new Regla("LIQUIDACION_NO_COINCIDE", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20019, new Regla("PLAZO_LEGAL", HttpStatus.UNPROCESSABLE_CONTENT)),
            Map.entry(20020, new Regla("ORDEN_NO_PROCEDENTE", HttpStatus.CONFLICT)),
            Map.entry(20021, new Regla("VALOR_EN_RECLAMO", HttpStatus.CONFLICT)));

    /** Mensajes para las restricciones que un usuario puede violar con datos válidos en forma. */
    private static final Map<String, String> UNICAS = Map.ofEntries(
            Map.entry("UK_USUARIO_IDENTIFICACION", "Ya existe un usuario con ese tipo y número de identificación"),
            Map.entry("UK_FUNC_USERNAME", "El nombre de usuario ya está en uso"),
            Map.entry("UK_FUNC_CORREO", "El correo ya está registrado para otro funcionario"),
            Map.entry("UK_FUNC_CODIGO", "El código de funcionario ya existe"),
            Map.entry("UK_MEDIDOR_SERIAL", "Ya existe un medidor con ese serial"),
            Map.entry("UK_MEDIDOR_PREDIO_TIPO", "El predio ya tiene un medidor para ese servicio"),
            Map.entry("UX_MEDIDOR_ACTIVO", "El predio ya tiene un medidor activo para ese servicio"),
            Map.entry("UK_PREDIO_FICHA", "Ya existe un predio con esa ficha catastral"),
            Map.entry("UX_CONTRATO_VIGENTE", "Ya existe un contrato vigente para ese predio y servicio (RN-01)"),
            Map.entry("UX_CONTRATO_MEDIDOR_VIGENTE", "El medidor ya está asociado a otro contrato vigente"),
            Map.entry("UK_LECTURA_PERIODO", "Ya existe una lectura para ese medidor y periodo"),
            Map.entry("UX_FACTURA_PREDIO_PERIODO", "Ya existe una factura vigente para ese predio y periodo"),
            Map.entry("UK_FACTURA_CLAVE", "Ya existe una factura vigente para ese predio y periodo"),
            Map.entry("UK_FACTURA_REEXPEDICION", "La factura anulada ya fue reexpedida"),
            Map.entry("UK_LINEA_CONTRATO", "El contrato ya está liquidado en esa factura"),
            Map.entry("UX_LOTE_PERIODO", "Ya hay un lote de facturación en curso o completado para ese periodo"),
            Map.entry("UK_PAGO_CUOTA", "La cuota ya tiene un pago registrado"),
            Map.entry("UK_PAGO_CODIGO_TX", "La transacción de la pasarela ya fue aplicada"),
            Map.entry("UK_PAGO_TRANSACCION", "La transacción de la pasarela ya fue aplicada"),
            Map.entry("UX_TRANSACCION_FACTURA_ABIERTA", "Ya hay un pago en línea en curso para esa factura"),
            Map.entry("UX_TRANSACCION_CUOTA_ABIERTA", "Ya hay un pago en línea en curso para esa cuota"),
            Map.entry("UK_TARIFA_VIGENCIA", "Ya existe una tarifa con esa vigencia para el mismo servicio y municipio"),
            Map.entry("UX_TARIFA_ABIERTA", "Ya hay una tarifa abierta para ese servicio y municipio; cierre su vigencia primero"),
            Map.entry("UK_FACTOR_VIGENCIA", "Ya existe un factor con esa vigencia"),
            Map.entry("UX_FACTOR_ABIERTO", "Ya hay un factor abierto para ese servicio, uso y estrato; cierre su vigencia primero"),
            Map.entry("UX_ACUERDO_VIGENTE", "El usuario ya tiene un acuerdo de pago vigente"),
            Map.entry("UX_COBRO_ABIERTO", "El usuario ya tiene un cobro persuasivo abierto"),
            Map.entry("UX_ORDEN_PENDIENTE", "El contrato ya tiene una orden pendiente de ese tipo"),
            Map.entry("UK_PQR_ORIGEN", "Esa respuesta ya fue recurrida"),
            Map.entry("UX_TOKEN_PENDIENTE", "Ya hay un enlace vigente; revise su correo"),
            Map.entry("UK_SECTOR_NOMBRE", "Ya existe un sector con ese nombre en el municipio"),
            Map.entry("UK_PQRADJ_HASH", "Ese archivo ya fue adjuntado a la PQR"));

    private static final Pattern MENSAJE_APLICACION = Pattern.compile("ORA-2\\d{4}: ([^\\n]*)");
    private static final Pattern RESTRICCION = Pattern.compile("\\(([A-Z0-9_$#]+)\\.([A-Z0-9_$#]+)\\)");

    private TraductorErroresOracle() {
    }

    public static Optional<NegocioException> traducir(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return traducir(sql);
            }
        }
        return Optional.empty();
    }

    private static Optional<NegocioException> traducir(SQLException sql) {
        int codigo = sql.getErrorCode();
        String texto = sql.getMessage() == null ? "" : sql.getMessage();
        Regla regla = APLICACION.get(codigo);
        if (regla != null) {
            Matcher m = MENSAJE_APLICACION.matcher(texto);
            String mensaje = m.find() ? m.group(1).trim() : "La operación no cumple una regla de negocio";
            return Optional.of(new NegocioException(regla.codigo(), mensaje, regla.status()));
        }
        if (codigo == 1) {
            String nombre = restriccion(texto);
            return Optional.of(new NegocioException("DUPLICADO",
                    UNICAS.getOrDefault(nombre, "El registro ya existe"), HttpStatus.CONFLICT));
        }
        if (codigo == 2091) {
            // Restricción diferida violada al confirmar (CK_FACTURA_COMPLETA, CK_ACUERDO_CUADRE)
            return Optional.of(new NegocioException("TRANSACCION_INCOMPLETA",
                    "La operación quedó incompleta o descuadrada y se revirtió (" + restriccion(texto) + ")",
                    HttpStatus.UNPROCESSABLE_CONTENT));
        }
        if (codigo == 2290) {
            return Optional.of(new NegocioException("DATO_INVALIDO",
                    "Un dato no cumple las reglas de formato o de negocio (" + restriccion(texto) + ")",
                    HttpStatus.UNPROCESSABLE_CONTENT));
        }
        if (codigo == 2291) {
            return Optional.of(new NegocioException("REFERENCIA_INEXISTENTE",
                    "Un registro relacionado no existe", HttpStatus.UNPROCESSABLE_CONTENT));
        }
        if (codigo == 2292) {
            return Optional.of(new NegocioException("REGISTRO_EN_USO",
                    "El registro tiene información relacionada y no se puede eliminar", HttpStatus.CONFLICT));
        }
        return Optional.empty();
    }

    private static String restriccion(String texto) {
        Matcher m = RESTRICCION.matcher(texto);
        return m.find() ? m.group(2) : "";
    }
}
