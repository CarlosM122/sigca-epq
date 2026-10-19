package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * Factura de un predio y periodo. Totales y estado PAGADA los mantiene la BD.
 * Mapeo de la tabla {@code FACTURA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "FACTURA")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "NUMERO_FACTURA", nullable = false, length = 20, insertable = false, updatable = false)
    private String numeroFactura;

    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "PREDIO_ID", nullable = false)
    private Long predioId;

    @Column(name = "LOTE_ID")
    private Long loteId;

    @Column(name = "PERIODO", nullable = false)
    private Integer periodo;

    @Column(name = "FECHA_EMISION", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "FECHA_VENCIMIENTO", nullable = false)
    private LocalDate fechaVencimiento;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "NUMERO_LINEAS", nullable = false, insertable = false, updatable = false)
    private Integer numeroLineas;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "TOTAL_A_PAGAR", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal totalAPagar;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "VALOR_AJUSTES", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal valorAjustes;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "VALOR_PAGADO", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal valorPagado;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "VALOR_EN_RECLAMO", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal valorEnReclamo;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "SALDO_PENDIENTE", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal saldoPendiente;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoFactura estado;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_ESTADO", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaEstado;

    @Column(name = "MOTIVO_ANULACION", length = 500)
    private String motivoAnulacion;

    @Column(name = "ANULADA_POR")
    private Long anuladaPor;

    @Column(name = "REEXPEDICION_DE_ID")
    private Long reexpedicionDeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_SYNC_ERP", nullable = false)
    private EstadoSyncErp estadoSyncErp;

    @Column(name = "REFERENCIA_ERP", length = 60)
    private String referenciaErp;

    @Column(name = "FECHA_SYNC_ERP")
    private LocalDateTime fechaSyncErp;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @CreatedBy
    @Column(name = "CREADO_POR", nullable = false, length = 100, updatable = false)
    private String creadoPor;

    @LastModifiedDate
    @Column(name = "ACTUALIZADO_EN")
    private LocalDateTime actualizadoEn;

    @LastModifiedBy
    @Column(name = "ACTUALIZADO_POR", length = 100)
    private String actualizadoPor;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
