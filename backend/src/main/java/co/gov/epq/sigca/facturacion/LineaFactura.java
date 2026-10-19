package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import co.gov.epq.sigca.common.persistencia.SiNoConverter;
import co.gov.epq.sigca.contratos.TipoServicio;

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
 * Línea de factura por contrato. TRG_LINEA_BI verifica la liquidación.
 * Mapeo de la tabla {@code LINEA_FACTURA} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "LINEA_FACTURA")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class LineaFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FACTURA_ID", nullable = false)
    private Long facturaId;

    @Column(name = "CONTRATO_ID", nullable = false)
    private Long contratoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_SERVICIO", nullable = false)
    private TipoServicio tipoServicio;

    @Column(name = "TARIFA_ID", nullable = false)
    private Long tarifaId;

    @Column(name = "FACTOR_SUBSIDIO_ID")
    private Long factorSubsidioId;

    @Column(name = "LECTURA_ID", nullable = false)
    private Long lecturaId;

    @Column(name = "CONSUMO", nullable = false, precision = 12, scale = 3)
    private BigDecimal consumo;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "CONSUMO_ESTIMADO", nullable = false, insertable = false, updatable = false)
    private Boolean consumoEstimado;

    @Column(name = "CONSUMO_BASICO", nullable = false, precision = 12, scale = 3)
    private BigDecimal consumoBasico;

    @Column(name = "CONSUMO_COMPLEMENTARIO", nullable = false, precision = 12, scale = 3)
    private BigDecimal consumoComplementario;

    @Column(name = "CONSUMO_SUNTUARIO", nullable = false, precision = 12, scale = 3)
    private BigDecimal consumoSuntuario;

    @Column(name = "CARGO_FIJO", nullable = false, precision = 14, scale = 2)
    private BigDecimal cargoFijo;

    @Column(name = "VALOR_CONSUMO", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorConsumo;

    @Column(name = "SUBSIDIO", nullable = false, precision = 14, scale = 2)
    private BigDecimal subsidio;

    @Column(name = "CONTRIBUCION", nullable = false, precision = 14, scale = 2)
    private BigDecimal contribucion;

    @Column(name = "OTROS_CARGOS", nullable = false, precision = 14, scale = 2)
    private BigDecimal otrosCargos;

    @Column(name = "DETALLE_OTROS_CARGOS", length = 300)
    private String detalleOtrosCargos;

    @Column(name = "INTERESES_MORA", nullable = false, precision = 14, scale = 2)
    private BigDecimal interesesMora;

    @Column(name = "TOTAL_LINEA", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalLinea;

    @Column(name = "VALOR_AJUSTE", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorAjuste;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, insertable = false, updatable = false)
    private EstadoLinea estado;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "EN_RECLAMO", nullable = false, insertable = false, updatable = false)
    private Boolean enReclamo;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "PAGO_ID", insertable = false, updatable = false)
    private Long pagoId;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_PAGO", insertable = false, updatable = false)
    private LocalDateTime fechaPago;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
