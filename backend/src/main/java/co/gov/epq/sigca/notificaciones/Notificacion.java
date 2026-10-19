package co.gov.epq.sigca.notificaciones;

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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Notificación pendiente o enviada (RN-23).
 * Mapeo de la tabla {@code NOTIFICACION} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "NOTIFICACION")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "EVENTO", nullable = false)
    private EventoNotificacion evento;

    @Enumerated(EnumType.STRING)
    @Column(name = "CANAL", nullable = false)
    private CanalNotificacion canal;

    @Column(name = "DESTINATARIO", length = 254)
    private String destinatario;

    @Column(name = "FUNCIONARIO_ID")
    private Long funcionarioId;

    @Column(name = "USUARIO_ID")
    private Long usuarioId;

    @Column(name = "ASUNTO", length = 200)
    private String asunto;

    @Lob
    @Column(name = "CUERPO", nullable = false)
    private String cuerpo;

    @Column(name = "ENTIDAD", length = 30)
    private String entidad;

    @Column(name = "ENTIDAD_ID")
    private Long entidadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoNotificacion estado;

    @Column(name = "INTENTOS", nullable = false)
    private Integer intentos;

    @Column(name = "PROXIMO_INTENTO", nullable = false)
    private LocalDateTime proximoIntento;

    @Column(name = "ULTIMO_ERROR", length = 500)
    private String ultimoError;

    @Column(name = "ENVIADA_EN")
    private LocalDateTime enviadaEn;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "LEIDA", nullable = false)
    private Boolean leida;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
