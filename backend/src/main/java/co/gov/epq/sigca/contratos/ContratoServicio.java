package co.gov.epq.sigca.contratos;

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
 * Contrato de un servicio en un predio (RF-04). Suspender y reconectar solo con órdenes.
 * Mapeo de la tabla {@code CONTRATO_SERVICIO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "CONTRATO_SERVICIO")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class ContratoServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "NUMERO_CONTRATO", nullable = false, length = 12, insertable = false, updatable = false)
    private String numeroContrato;

    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "PREDIO_ID", nullable = false)
    private Long predioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_SERVICIO", nullable = false)
    private TipoServicio tipoServicio;

    @Column(name = "MEDIDOR_ID")
    private Long medidorId;

    @Column(name = "FECHA_VINCULACION", nullable = false)
    private LocalDate fechaVinculacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoContrato estado;

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
