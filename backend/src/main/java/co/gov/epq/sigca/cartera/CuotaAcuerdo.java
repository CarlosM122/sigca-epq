package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuota de un acuerdo; la 0 es la cuota inicial.
 * Mapeo de la tabla {@code CUOTA_ACUERDO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "CUOTA_ACUERDO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class CuotaAcuerdo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACUERDO_ID", nullable = false)
    private Long acuerdoId;

    @Column(name = "NUMERO_CUOTA", nullable = false)
    private Integer numeroCuota;

    @Column(name = "VALOR_CAPITAL", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorCapital;

    @Column(name = "VALOR_INTERES", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorInteres;

    @Column(name = "VALOR_CUOTA", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorCuota;

    @Column(name = "FECHA_VENCIMIENTO", nullable = false)
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoCuota estado;

    @Column(name = "FECHA_PAGO")
    private LocalDateTime fechaPago;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
