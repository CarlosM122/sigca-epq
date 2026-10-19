package co.gov.epq.sigca.usuarios;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
 * Inmueble donde se prestan los servicios.
 * Mapeo de la tabla {@code PREDIO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PREDIO")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Predio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "CODIGO_PREDIO", nullable = false, length = 12, insertable = false, updatable = false)
    private String codigoPredio;

    @Column(name = "FICHA_CATASTRAL", length = 30)
    private String fichaCatastral;

    @Column(name = "DIRECCION", nullable = false, length = 200)
    private String direccion;

    @Column(name = "BARRIO_VEREDA", length = 100)
    private String barrioVereda;

    @Column(name = "MUNICIPIO_ID", nullable = false)
    private Long municipioId;

    @Column(name = "SECTOR_ID")
    private Long sectorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "USO", nullable = false)
    private UsoPredio uso;

    @Column(name = "ESTRATO")
    private Integer estrato;

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
