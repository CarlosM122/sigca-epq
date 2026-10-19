package co.gov.epq.sigca.pqr;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Soporte o evidencia de una PQR.
 * Mapeo de la tabla {@code PQR_ADJUNTO} (esquema v2). Generado a partir del diccionario de datos.
 */
@Entity
@Table(name = "PQR_ADJUNTO")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
public class PqrAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "PQR_ID", nullable = false)
    private Long pqrId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false)
    private TipoAdjunto tipo;

    @Column(name = "NOMBRE_ARCHIVO", nullable = false, length = 200)
    private String nombreArchivo;

    @Column(name = "TIPO_MIME", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "TAMANO_BYTES", nullable = false)
    private Long tamanoBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "HASH_SHA256", nullable = false, length = 64)
    private String hashSha256;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENIDO", nullable = false)
    private byte[] contenido;

    @Column(name = "CARGADO_POR")
    private Long cargadoPor;

    @Generated(event = EventType.INSERT)
    @Column(name = "CARGADO_EN", nullable = false, insertable = false, updatable = false)
    private LocalDateTime cargadoEn;
}
