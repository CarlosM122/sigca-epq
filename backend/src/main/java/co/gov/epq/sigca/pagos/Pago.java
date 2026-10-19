package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.gov.epq.sigca.common.persistencia.SiNoConverter;
import co.gov.epq.sigca.facturacion.EstadoSyncErp;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Pago confirmado de una factura o una cuota (RF-06).
 * Mapeo de la tabla {@code PAGO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PAGO")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "CODIGO_PAGO", nullable = false, length = 14, insertable = false, updatable = false)
    private String codigoPago;

    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "FACTURA_ID")
    private Long facturaId;

    @Column(name = "CUOTA_ID")
    private Long cuotaId;

    @Generated(event = EventType.INSERT)
    @Column(name = "FECHA_PAGO", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaPago;

    @Column(name = "VALOR", nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "CANAL_PAGO", nullable = false)
    private CanalPago canalPago;

    @Column(name = "TRANSACCION_PASARELA_ID")
    private Long transaccionPasarelaId;

    @Column(name = "CODIGO_TRANSACCION", length = 60)
    private String codigoTransaccion;

    @Column(name = "REFERENCIA_BANCO", length = 60)
    private String referenciaBanco;

    @Column(name = "FUNCIONARIO_ID")
    private Long funcionarioId;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "COMPROBANTE_ENVIADO", nullable = false)
    private Boolean comprobanteEnviado;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO_SYNC_ERP", nullable = false)
    private EstadoSyncErp estadoSyncErp;

    @Column(name = "REFERENCIA_ERP", length = 60)
    private String referenciaErp;

    @Column(name = "FECHA_SYNC_ERP")
    private LocalDateTime fechaSyncErp;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @CreatedBy
    @Column(name = "CREADO_POR", nullable = false, length = 100, updatable = false)
    private String creadoPor;
}
