package co.gov.epq.sigca.seguridad.cuentas;

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
 * Rol del RBAC (P-16).
 * Mapeo de la tabla {@code ROL} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "ROL")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODIGO", nullable = false, length = 30)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, length = 80)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 300)
    private String descripcion;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo;
}
