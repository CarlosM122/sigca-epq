package co.gov.epq.sigca.seguridad.cuentas;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuenta del portal ciudadano; el usuario ingresa con su identificación (P-13).
 * Mapeo de la tabla {@code CUENTA_PORTAL} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "CUENTA_PORTAL")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class CuentaPortal {

    @Id
    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoCuentaPortal estado;

    @Column(name = "PASSWORD_HASH", length = 255)
    private String passwordHash;

    @Column(name = "INTENTOS_FALLIDOS", nullable = false)
    private Integer intentosFallidos;

    @Column(name = "BLOQUEADA_HASTA")
    private LocalDateTime bloqueadaHasta;

    @Column(name = "ULTIMO_ACCESO")
    private LocalDateTime ultimoAcceso;

    @Column(name = "CAMBIO_PASSWORD_EN")
    private LocalDateTime cambioPasswordEn;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
