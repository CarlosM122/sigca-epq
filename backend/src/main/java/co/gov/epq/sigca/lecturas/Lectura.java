package co.gov.epq.sigca.lecturas;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Lectura mensual de un medidor. La BD calcula consumo, crítica y estimación (P-07).
 * Mapeo de la tabla {@code LECTURA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "LECTURA")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Lectura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MEDIDOR_ID", nullable = false)
    private Long medidorId;

    @Column(name = "PERIODO", nullable = false)
    private Integer periodo;

    @Column(name = "FECHA_LECTURA", nullable = false)
    private LocalDate fechaLectura;

    @Generated(event = EventType.INSERT)
    @Column(name = "LECTURA_ANTERIOR", nullable = false, precision = 12, scale = 3, insertable = false, updatable = false)
    private BigDecimal lecturaAnterior;

    @Column(name = "LECTURA_ACTUAL", precision = 12, scale = 3)
    private BigDecimal lecturaActual;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "CONSUMO", precision = 12, scale = 3, insertable = false, updatable = false)
    private BigDecimal consumo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoLectura estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEN", nullable = false)
    private OrigenLectura origen;

    @Enumerated(EnumType.STRING)
    @Column(name = "CAUSA_NO_LECTURA")
    private CausaNoLectura causaNoLectura;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "PROMEDIO_REFERENCIA", precision = 12, scale = 3, insertable = false, updatable = false)
    private BigDecimal promedioReferencia;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "DESVIACION_PCT", precision = 9, scale = 2, insertable = false, updatable = false)
    private BigDecimal desviacionPct;

    @Column(name = "OBSERVACION", length = 300)
    private String observacion;

    @Column(name = "REGISTRADA_POR")
    private Long registradaPor;

    @Column(name = "REVISADA_POR")
    private Long revisadaPor;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_REVISION", insertable = false, updatable = false)
    private LocalDateTime fechaRevision;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
