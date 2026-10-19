package co.gov.epq.sigca.pqr;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Traza de estados y responsables; la escribe la BD.
 * Mapeo de la tabla {@code PQR_HISTORIAL} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PQR_HISTORIAL")
@Immutable
@DynamicInsert
@DynamicUpdate
@Getter
public class PqrHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "PQR_ID", nullable = false)
    private Long pqrId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_ANTERIOR")
    private EstadoPqr estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_NUEVO", nullable = false)
    private EstadoPqr estadoNuevo;

    @Column(name = "GESTOR_ANTERIOR")
    private Long gestorAnterior;

    @Column(name = "GESTOR_NUEVO")
    private Long gestorNuevo;

    @Column(name = "FECHA", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "REALIZADO_POR", nullable = false, length = 100)
    private String realizadoPor;
}
