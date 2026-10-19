package co.gov.epq.sigca.seguridad.cuentas;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Asignación de un rol a un funcionario.
 * Mapeo de la tabla {@code FUNCIONARIO_ROL} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "FUNCIONARIO_ROL")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@IdClass(FuncionarioRol.Clave.class)
@Getter
@Setter
public class FuncionarioRol {

    @Id
    @Column(name = "FUNCIONARIO_ID", nullable = false)
    private Long funcionarioId;

    @Id
    @Column(name = "ROL_ID", nullable = false)
    private Long rolId;

    @Generated(event = EventType.INSERT)
    @Column(name = "ASIGNADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime asignadoEn;

    @CreatedBy
    @Column(name = "ASIGNADO_POR", nullable = false, length = 100, updatable = false)
    private String asignadoPor;

    /** Clave compuesta. */
    public record Clave(Long funcionarioId, Long rolId) implements Serializable {
    }
}
