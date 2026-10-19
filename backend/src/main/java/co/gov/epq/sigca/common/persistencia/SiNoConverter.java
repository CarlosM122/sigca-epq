package co.gov.epq.sigca.common.persistencia;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Las banderas del modelo Oracle son CHAR(1) con 'S'/'N'. Usar junto con
 * {@code @JdbcTypeCode(SqlTypes.CHAR)} para que la validación del esquema coincida.
 */
@Converter
public class SiNoConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean valor) {
        return valor == null ? null : (valor ? "S" : "N");
    }

    @Override
    public Boolean convertToEntityAttribute(String valor) {
        if (valor == null) {
            return null;
        }
        return switch (valor) {
            case "S" -> Boolean.TRUE;
            case "N" -> Boolean.FALSE;
            default -> throw new IllegalStateException("Valor de bandera no válido en BD: " + valor);
        };
    }
}
