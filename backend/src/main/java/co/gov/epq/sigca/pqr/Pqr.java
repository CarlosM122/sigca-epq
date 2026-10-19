package co.gov.epq.sigca.pqr;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import co.gov.epq.sigca.common.persistencia.SiNoConverter;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Petición, queja, reclamo o recurso (RF-08, RF-09).
 * Mapeo de la tabla {@code PQR} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PQR")
@DynamicInsert
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Pqr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Generated(event = EventType.INSERT)
    @Column(name = "NUMERO_RADICADO", nullable = false, length = 11, insertable = false, updatable = false)
    private String numeroRadicado;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false)
    private TipoPqr tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "CANAL", nullable = false)
    private CanalPqr canal;

    @Column(name = "CONTRATO_ID")
    private Long contratoId;

    @Column(name = "USUARIO_ID")
    private Long usuarioId;

    @Column(name = "MUNICIPIO_ID", nullable = false)
    private Long municipioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "SOLICITANTE_TIPO_IDENTIFICACION")
    private TipoIdentificacion solicitanteTipoIdentificacion;

    @Column(name = "SOLICITANTE_IDENTIFICACION_CIFRADO", length = 255)
    private String solicitanteIdentificacionCifrado;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "SOLICITANTE_IDENTIFICACION_HASH", length = 64)
    private String solicitanteIdentificacionHash;

    @Column(name = "SOLICITANTE_IDENTIFICACION_MASCARA", length = 20)
    private String solicitanteIdentificacionMascara;

    @Column(name = "PQR_ORIGEN_ID")
    private Long pqrOrigenId;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "SOLICITA_APELACION", nullable = false)
    private Boolean solicitaApelacion;

    @Column(name = "LINEA_FACTURA_ID")
    private Long lineaFacturaId;

    @Lob
    @Column(name = "DESCRIPCION", nullable = false)
    private String descripcion;

    @Column(name = "NOMBRE_CONTACTO", nullable = false, length = 150)
    private String nombreContacto;

    @Column(name = "CORREO_CONTACTO", nullable = false, length = 254)
    private String correoContacto;

    @Column(name = "TELEFONO_CONTACTO", nullable = false, length = 13)
    private String telefonoContacto;

    @Generated(event = EventType.INSERT)
    @Column(name = "FECHA_REGISTRO", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Generated(event = EventType.INSERT)
    @Column(name = "FECHA_LIMITE_RESPUESTA", nullable = false, insertable = false, updatable = false)
    private LocalDate fechaLimiteRespuesta;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false)
    private EstadoPqr estado;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_ESTADO", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaEstado;

    @Column(name = "GESTOR_ID", nullable = false)
    private Long gestorId;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_ASIGNACION", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaAsignacion;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_INICIO_TRAMITE", insertable = false, updatable = false)
    private LocalDateTime fechaInicioTramite;

    @Lob
    @Column(name = "RESPUESTA")
    private String respuesta;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_RESPUESTA", insertable = false, updatable = false)
    private LocalDateTime fechaRespuesta;

    @Column(name = "RESPONDIDA_POR")
    private Long respondidaPor;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "RESPUESTA_EXTEMPORANEA", nullable = false, insertable = false, updatable = false)
    private Boolean respuestaExtemporanea;

    @Enumerated(EnumType.STRING)
    @Column(name = "DECISION_RECURSO")
    private DecisionRecurso decisionRecurso;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_TRASLADO_SSPD", insertable = false, updatable = false)
    private LocalDateTime fechaTrasladoSspd;

    @Enumerated(EnumType.STRING)
    @Column(name = "DECISION_SSPD")
    private DecisionRecurso decisionSspd;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_DECISION_SSPD", insertable = false, updatable = false)
    private LocalDateTime fechaDecisionSspd;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "SILENCIO_POSITIVO", nullable = false, insertable = false, updatable = false)
    private Boolean silencioPositivo;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "FECHA_LIMITE_RECONOCIMIENTO", insertable = false, updatable = false)
    private LocalDateTime fechaLimiteReconocimiento;

    @Column(name = "FECHA_RECONOCIMIENTO_SILENCIO")
    private LocalDateTime fechaReconocimientoSilencio;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ALERTA_48H_ENVIADA", nullable = false)
    private Boolean alerta48hEnviada;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "REPORTADA_SSPD", nullable = false)
    private Boolean reportadaSspd;

    @Column(name = "REGISTRADA_POR")
    private Long registradaPor;

    @Generated(event = EventType.INSERT)
    @Column(name = "CREADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;

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
