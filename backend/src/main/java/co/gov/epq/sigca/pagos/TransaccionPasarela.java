package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Intento de pago en línea (RN-05, P-18).
 * Mapeo de la tabla {@code TRANSACCION_PASARELA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "TRANSACCION_PASARELA")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class TransaccionPasarela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REFERENCIA", nullable = false, length = 40)
    private String referencia;

    @Column(name = "REFERENCIA_PAGO", nullable = false, length = 20)
    private String referenciaPago;

    @Column(name = "FACTURA_ID")
    private Long facturaId;

    @Column(name = "CUOTA_ID")
    private Long cuotaId;

    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "MONTO", nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoTransaccion estado;

    @Column(name = "CODIGO_TRANSACCION", length = 60)
    private String codigoTransaccion;

    @Column(name = "CODIGO_RESPUESTA", length = 20)
    private String codigoRespuesta;

    @Column(name = "MENSAJE_RESPUESTA", length = 300)
    private String mensajeRespuesta;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FIRMA_VALIDADA", nullable = false)
    private Boolean firmaValidada;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "INTENTOS", nullable = false, insertable = false, updatable = false)
    private Integer intentos;

    @Column(name = "IP_ORIGEN", length = 45)
    private String ipOrigen;

    @Generated(event = EventType.INSERT)
    @Column(name = "INICIADA_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime iniciadaEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "RESPONDIDA_EN", insertable = false, updatable = false)
    private LocalDateTime respondidaEn;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
