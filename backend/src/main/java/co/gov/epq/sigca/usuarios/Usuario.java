package co.gov.epq.sigca.usuarios;

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
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.common.validation.TipoPersona;

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
 * Titular de contratos (RF-01). La identificación se guarda cifrada, con hash y máscara.
 * Mapeo de la tabla {@code USUARIO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "USUARIO")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "CODIGO", nullable = false, length = 12, insertable = false, updatable = false)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_PERSONA", nullable = false)
    private TipoPersona tipoPersona;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_IDENTIFICACION", nullable = false)
    private TipoIdentificacion tipoIdentificacion;

    @Column(name = "NUMERO_IDENTIFICACION_CIFRADO", nullable = false, length = 255)
    private String numeroIdentificacionCifrado;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "NUMERO_IDENTIFICACION_HASH", nullable = false, length = 64)
    private String numeroIdentificacionHash;

    @Column(name = "NUMERO_IDENTIFICACION_MASCARA", nullable = false, length = 20)
    private String numeroIdentificacionMascara;

    @Column(name = "NOMBRE_COMPLETO", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "CORREO", nullable = false, length = 254)
    private String correo;

    @Column(name = "TELEFONO", nullable = false, length = 13)
    private String telefono;

    @Column(name = "DIRECCION_NOTIFICACION", nullable = false, length = 200)
    private String direccionNotificacion;

    @Column(name = "MUNICIPIO_ID", nullable = false)
    private Long municipioId;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "AUTORIZA_TRATAMIENTO_DATOS", nullable = false)
    private Boolean autorizaTratamientoDatos;

    @Column(name = "FECHA_AUTORIZACION_DATOS", nullable = false)
    private LocalDateTime fechaAutorizacionDatos;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_VERIFICACION_RNEC", nullable = false)
    private EstadoVerificacionRnec estadoVerificacionRnec;

    @Column(name = "FECHA_VERIFICACION_RNEC")
    private LocalDateTime fechaVerificacionRnec;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo;

    @Column(name = "FECHA_INACTIVACION")
    private LocalDateTime fechaInactivacion;

    @Column(name = "MOTIVO_INACTIVACION", length = 300)
    private String motivoInactivacion;

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
