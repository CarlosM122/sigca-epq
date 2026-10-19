package co.gov.epq.sigca.tarifas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.usuarios.UsoPredio;

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
 * Subsidio (negativo) o contribución (positivo) por estrato o uso (P-08).
 * Mapeo de la tabla {@code FACTOR_SUBSIDIO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "FACTOR_SUBSIDIO")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class FactorSubsidio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MUNICIPIO_ID", nullable = false)
    private Long municipioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_SERVICIO", nullable = false)
    private TipoServicio tipoServicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "USO", nullable = false)
    private UsoPredio uso;

    @Column(name = "ESTRATO")
    private Integer estrato;

    @Column(name = "PORCENTAJE", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(name = "VIGENTE_DESDE", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "VIGENTE_HASTA")
    private LocalDate vigenteHasta;

    @Column(name = "ACUERDO_MUNICIPAL", nullable = false, length = 80)
    private String acuerdoMunicipal;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @CreatedBy
    @Column(name = "CREADO_POR", nullable = false, length = 100, updatable = false)
    private String creadoPor;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
