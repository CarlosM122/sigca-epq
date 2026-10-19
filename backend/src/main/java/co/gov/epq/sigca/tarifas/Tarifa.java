package co.gov.epq.sigca.tarifas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.gov.epq.sigca.contratos.TipoServicio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Tarifa CRA por servicio y municipio con vigencia (P-08).
 * Mapeo de la tabla {@code TARIFA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "TARIFA")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Tarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_SERVICIO", nullable = false)
    private TipoServicio tipoServicio;

    @Column(name = "MUNICIPIO_ID", nullable = false)
    private Long municipioId;

    @Column(name = "CARGO_FIJO", nullable = false, precision = 14, scale = 2)
    private BigDecimal cargoFijo;

    @Column(name = "VALOR_M3_BASICO", nullable = false, precision = 14, scale = 4)
    private BigDecimal valorM3Basico;

    @Column(name = "VALOR_M3_COMPLEMENTARIO", precision = 14, scale = 4)
    private BigDecimal valorM3Complementario;

    @Column(name = "VALOR_M3_SUNTUARIO", precision = 14, scale = 4)
    private BigDecimal valorM3Suntuario;

    @Column(name = "CONSUMO_BASICO_M3", precision = 6, scale = 2)
    private BigDecimal consumoBasicoM3;

    @Column(name = "CONSUMO_SUNTUARIO_DESDE_M3", precision = 6, scale = 2)
    private BigDecimal consumoSuntuarioDesdeM3;

    @Column(name = "VIGENTE_DESDE", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "VIGENTE_HASTA")
    private LocalDate vigenteHasta;

    @Column(name = "RESOLUCION", nullable = false, length = 80)
    private String resolucion;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @CreatedBy
    @Column(name = "CREADO_POR", nullable = false, length = 100, updatable = false)
    private String creadoPor;

    @LastModifiedDate
    @Column(name = "ACTUALIZADO_EN")
    private LocalDateTime actualizadoEn;

    @LastModifiedBy
    @Column(name = "ACTUALIZADO_POR", length = 100)
    private String actualizadoPor;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
