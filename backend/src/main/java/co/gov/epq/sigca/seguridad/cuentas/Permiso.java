package co.gov.epq.sigca.seguridad.cuentas;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Permiso atómico del RBAC.
 * Mapeo de la tabla {@code PERMISO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PERMISO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODIGO", nullable = false, length = 60)
    private String codigo;

    @Column(name = "MODULO", nullable = false, length = 30)
    private String modulo;

    @Column(name = "DESCRIPCION", nullable = false, length = 300)
    private String descripcion;
}
