package co.gov.epq.sigca.catalogo;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import co.gov.epq.sigca.common.persistencia.SiNoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Sector rural o corregimiento de un municipio (P-02).
 * Mapeo de la tabla {@code SECTOR} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "SECTOR")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MUNICIPIO_ID", nullable = false)
    private Long municipioId;

    @Column(name = "NOMBRE", nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false)
    private TipoSector tipo;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo;
}
