package co.gov.epq.sigca.cartera;

import java.io.Serializable;
import java.math.BigDecimal;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Factura vencida incluida en un acuerdo.
 * Mapeo de la tabla {@code ACUERDO_FACTURA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "ACUERDO_FACTURA")
@DynamicInsert
@DynamicUpdate
@IdClass(AcuerdoFactura.Clave.class)
@Getter
@Setter
public class AcuerdoFactura {

    @Id
    @Column(name = "ACUERDO_ID", nullable = false)
    private Long acuerdoId;

    @Id
    @Column(name = "FACTURA_ID", nullable = false)
    private Long facturaId;

    @Column(name = "VALOR_INCLUIDO", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorIncluido;

    /** Clave compuesta. */
    public record Clave(Long acuerdoId, Long facturaId) implements Serializable {
    }
}
