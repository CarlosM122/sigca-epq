package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedBy;

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
 * Ejecución del lote mensual de facturación (RF-05).
 * Mapeo de la tabla {@code LOTE_FACTURACION} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "LOTE_FACTURACION")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class LoteFacturacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "PERIODO", nullable = false)
    private Integer periodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoLote estado;

    @Column(name = "INICIADO_EN", nullable = false)
    private LocalDateTime iniciadoEn;

    @Column(name = "FINALIZADO_EN")
    private LocalDateTime finalizadoEn;

    @Column(name = "TOTAL_PREDIOS", nullable = false)
    private Long totalPredios;

    @Column(name = "TOTAL_FACTURAS", nullable = false)
    private Long totalFacturas;

    @Column(name = "TOTAL_ERRORES", nullable = false)
    private Long totalErrores;

    @Column(name = "VALOR_TOTAL", nullable = false, precision = 16, scale = 2)
    private BigDecimal valorTotal;

    @Lob
    @Column(name = "DETALLE_ERRORES")
    private String detalleErrores;

    @CreatedBy
    @Column(name = "EJECUTADO_POR", nullable = false, length = 100, updatable = false)
    private String ejecutadoPor;
}
