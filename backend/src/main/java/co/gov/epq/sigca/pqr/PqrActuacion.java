package co.gov.epq.sigca.pqr;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Actuación documentada durante el trámite.
 * Mapeo de la tabla {@code PQR_ACTUACION} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PQR_ACTUACION")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class PqrActuacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "PQR_ID", nullable = false)
    private Long pqrId;

    @Column(name = "FUNCIONARIO_ID", nullable = false)
    private Long funcionarioId;

    @Generated(event = EventType.INSERT)
    @Column(name = "FECHA", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "DESCRIPCION", nullable = false, length = 2000)
    private String descripcion;
}
