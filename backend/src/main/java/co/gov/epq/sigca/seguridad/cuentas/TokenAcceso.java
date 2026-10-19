package co.gov.epq.sigca.seguridad.cuentas;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Enlace de activación o recuperación; se guarda solo el hash.
 * Mapeo de la tabla {@code TOKEN_ACCESO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "TOKEN_ACCESO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class TokenAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false)
    private TipoToken tipo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "TOKEN_HASH", nullable = false, length = 64)
    private String tokenHash;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "EXPIRA_EN", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "USADO_EN")
    private LocalDateTime usadoEn;
}
