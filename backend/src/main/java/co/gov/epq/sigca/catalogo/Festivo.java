package co.gov.epq.sigca.catalogo;

import java.time.LocalDate;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Día no hábil para el cálculo de plazos (P-09).
 * Mapeo de la tabla {@code FESTIVO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "FESTIVO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Festivo {

    @Id
    @Column(name = "FECHA", nullable = false)
    private LocalDate fecha;

    @Column(name = "DESCRIPCION", nullable = false, length = 100)
    private String descripcion;
}
