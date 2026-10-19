package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import co.gov.epq.sigca.common.persistencia.SiNoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Gestión de cobro persuasivo de un usuario en mora (RF-23).
 * Mapeo de la tabla {@code COBRO_PERSUASIVO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "COBRO_PERSUASIVO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class CobroPersuasivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "VALOR_DEUDA", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorDeuda;

    @Column(name = "PERIODOS_MORA", nullable = false)
    private Integer periodosMora;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoCobro estado;

    @Column(name = "ACUERDO_ID")
    private Long acuerdoId;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CANDIDATO_SUSPENSION", nullable = false)
    private Boolean candidatoSuspension;

    @Column(name = "FECHA_AVISO_SUSPENSION")
    private LocalDateTime fechaAvisoSuspension;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_ESTADO", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaEstado;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    @Column(name = "FUNCIONARIO_ID")
    private Long funcionarioId;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
