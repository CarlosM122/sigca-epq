package co.gov.epq.sigca.catalogo;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import co.gov.epq.sigca.common.persistencia.SiNoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Municipio atendido por EPQ (P-02).
 * Mapeo de la tabla {@code MUNICIPIO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "MUNICIPIO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Municipio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODIGO_DANE", nullable = false, length = 5)
    private String codigoDane;

    @Column(name = "NOMBRE", nullable = false, length = 60)
    private String nombre;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo;
}
