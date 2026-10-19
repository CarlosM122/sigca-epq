package co.gov.epq.sigca.contratos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
 * Orden de suspensión o reconexión (P-11).
 * Mapeo de la tabla {@code ORDEN_SERVICIO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "ORDEN_SERVICIO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class OrdenServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "NUMERO_ORDEN", nullable = false, length = 12, insertable = false, updatable = false)
    private String numeroOrden;

    @Column(name = "CONTRATO_ID", nullable = false)
    private Long contratoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false)
    private TipoOrden tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "CAUSA", nullable = false)
    private CausaOrden causa;

    @Column(name = "COBRO_ID")
    private Long cobroId;

    @Column(name = "PAGO_ID")
    private Long pagoId;

    @Column(name = "ACUERDO_ID")
    private Long acuerdoId;

    @Generated(event = EventType.INSERT)
    @Column(name = "FECHA_ORDEN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaOrden;

    @Generated(event = EventType.INSERT)
    @Column(name = "FECHA_LIMITE", insertable = false, updatable = false)
    private LocalDateTime fechaLimite;

    @Generated(event = EventType.INSERT)
    @Column(name = "VALOR_CARGO", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal valorCargo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoOrden estado;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_EJECUCION", insertable = false, updatable = false)
    private LocalDateTime fechaEjecucion;

    @Column(name = "EJECUTADA_POR")
    private Long ejecutadaPor;

    @Column(name = "MOTIVO_ANULACION", length = 300)
    private String motivoAnulacion;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_POR", nullable = false, length = 100, insertable = false, updatable = false)
    private String creadoPor;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
