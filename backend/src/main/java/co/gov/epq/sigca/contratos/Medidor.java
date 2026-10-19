package co.gov.epq.sigca.contratos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.gov.epq.sigca.common.persistencia.SiNoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Medidor de acueducto o gas instalado en un predio.
 * Mapeo de la tabla {@code MEDIDOR} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "MEDIDOR")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Medidor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERIAL", nullable = false, length = 30)
    private String serial;

    @Column(name = "PREDIO_ID", nullable = false)
    private Long predioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_SERVICIO", nullable = false)
    private TipoServicio tipoServicio;

    @Column(name = "MARCA", length = 60)
    private String marca;

    @Column(name = "FECHA_INSTALACION", nullable = false)
    private LocalDate fechaInstalacion;

    @Column(name = "LECTURA_INICIAL", nullable = false, precision = 12, scale = 3)
    private BigDecimal lecturaInicial;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "LECTURA_ACTUAL", nullable = false, precision = 12, scale = 3, insertable = false, updatable = false)
    private BigDecimal lecturaActual;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "ULTIMO_PERIODO", insertable = false, updatable = false)
    private Integer ultimoPeriodo;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "LECTURA_EN_REVISION", nullable = false, insertable = false, updatable = false)
    private Boolean lecturaEnRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoMedidor estado;

    @Column(name = "FECHA_RETIRO")
    private LocalDate fechaRetiro;

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
