package co.gov.epq.sigca.auditoria;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
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
 * Registro inmutable de auditoría (RF-15).
 * Mapeo de la tabla {@code LOG_AUDITORIA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "LOG_AUDITORIA")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "FECHA", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTOR_TIPO", nullable = false)
    private ActorTipo actorTipo;

    @Column(name = "FUNCIONARIO_ID")
    private Long funcionarioId;

    @Column(name = "USUARIO_ID")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERACION", nullable = false)
    private OperacionAuditoria operacion;

    @Column(name = "ENTIDAD", length = 30)
    private String entidad;

    @Column(name = "ENTIDAD_ID")
    private Long entidadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "RESULTADO", nullable = false)
    private ResultadoAuditoria resultado;

    @Lob
    @Column(name = "DETALLE")
    private String detalle;

    @Column(name = "IP_ORIGEN", length = 45)
    private String ipOrigen;
}
