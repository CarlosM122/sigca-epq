package co.gov.epq.sigca.cartera;

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
 * Acuerdo de pago (RF-12, P-12).
 * Mapeo de la tabla {@code ACUERDO_PAGO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "ACUERDO_PAGO")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class AcuerdoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "NUMERO_ACUERDO", nullable = false, length = 14, insertable = false, updatable = false)
    private String numeroAcuerdo;

    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "FECHA_ACUERDO", nullable = false)
    private LocalDate fechaAcuerdo;

    @Column(name = "TOTAL_DEUDA", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeuda;

    @Column(name = "CUOTA_INICIAL", nullable = false, precision = 14, scale = 2)
    private BigDecimal cuotaInicial;

    @Column(name = "TASA_INTERES_ANUAL", nullable = false, precision = 7, scale = 4)
    private BigDecimal tasaInteresAnual;

    @Column(name = "NUMERO_CUOTAS", nullable = false)
    private Integer numeroCuotas;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "TOTAL_INCLUIDO", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal totalIncluido;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "TOTAL_CAPITAL_CUOTAS", nullable = false, precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal totalCapitalCuotas;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "CUOTAS_REGISTRADAS", nullable = false, insertable = false, updatable = false)
    private Integer cuotasRegistradas;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoAcuerdo estado;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_ESTADO", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaEstado;

    @Column(name = "MOTIVO_ESTADO", length = 300)
    private String motivoEstado;

    @Column(name = "FUNCIONARIO_ID", nullable = false)
    private Long funcionarioId;

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
