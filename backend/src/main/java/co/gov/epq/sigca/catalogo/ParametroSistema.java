package co.gov.epq.sigca.catalogo;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Parámetro de negocio (RF-29). Rangos legales en TRG_PARAMETRO_CIU.
 * Mapeo de la tabla {@code PARAMETRO_SISTEMA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PARAMETRO_SISTEMA")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class ParametroSistema {

    @Id
    @Column(name = "CLAVE", nullable = false, length = 60)
    private String clave;

    @Column(name = "VALOR", nullable = false, length = 400)
    private String valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_DATO", nullable = false)
    private TipoDato tipoDato;

    @Column(name = "DESCRIPCION", nullable = false, length = 300)
    private String descripcion;

    @LastModifiedDate
    @Column(name = "ACTUALIZADO_EN", nullable = false)
    private LocalDateTime actualizadoEn;

    @LastModifiedBy
    @Column(name = "ACTUALIZADO_POR", nullable = false, length = 100)
    private String actualizadoPor;
}
