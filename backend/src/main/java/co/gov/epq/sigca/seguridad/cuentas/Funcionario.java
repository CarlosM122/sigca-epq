package co.gov.epq.sigca.seguridad.cuentas;

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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Funcionario de EPQ con acceso al back-office. 2FA obligatorio (P-20).
 * Mapeo de la tabla {@code FUNCIONARIO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "FUNCIONARIO")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Funcionario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODIGO_FUNCIONARIO", nullable = false, length = 20)
    private String codigoFuncionario;

    @Column(name = "NOMBRE_COMPLETO", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "CORREO", nullable = false, length = 254)
    private String correo;

    @Column(name = "TELEFONO", length = 13)
    private String telefono;

    @Column(name = "USERNAME", nullable = false, length = 60)
    private String username;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CAMBIO_PASSWORD_REQUERIDO", nullable = false)
    private Boolean cambioPasswordRequerido;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "DOBLE_FACTOR_ACTIVO", nullable = false)
    private Boolean dobleFactorActivo;

    @Column(name = "SECRETO_DOBLE_FACTOR", length = 255)
    private String secretoDobleFactor;

    @Column(name = "INTENTOS_FALLIDOS", nullable = false)
    private Integer intentosFallidos;

    @Column(name = "BLOQUEADO_HASTA")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ULTIMO_ACCESO")
    private LocalDateTime ultimoAcceso;

    @Column(name = "MUNICIPIO_ID")
    private Long municipioId;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo;

    @Column(name = "FECHA_DESACTIVACION")
    private LocalDateTime fechaDesactivacion;

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
