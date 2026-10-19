package co.gov.epq.sigca.seguridad.cuentas;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Enlaces de activación y recuperación. Las fechas se calculan con el reloj de la BD (CREADO_EN = SYSTIMESTAMP)
 * para que CK_TOKEN_EXPIRA y CK_TOKEN_USO no dependan de la zona horaria de la JVM.
 */
public interface TokenAccesoRepository extends JpaRepository<TokenAcceso, Long> {

    Optional<TokenAcceso> findByTokenHash(String tokenHash);

    /**
     * Invalida los enlaces sin usar del mismo tipo (UX_TOKEN_PENDIENTE admite uno solo). Un enlace vencido se
     * marca en su fecha de vencimiento para respetar CK_TOKEN_USO.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE {h-schema}TOKEN_ACCESO
               SET USADO_EN = LEAST(CAST(SYSTIMESTAMP AS TIMESTAMP), EXPIRA_EN)
             WHERE USUARIO_ID = :usuarioId AND TIPO = :tipo AND USADO_EN IS NULL
            """)
    int invalidarPendientes(@Param("usuarioId") Long usuarioId, @Param("tipo") String tipo);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO {h-schema}TOKEN_ACCESO (USUARIO_ID, TIPO, TOKEN_HASH, EXPIRA_EN)
            VALUES (:usuarioId, :tipo, :hash, CAST(SYSTIMESTAMP AS TIMESTAMP) + NUMTODSINTERVAL(:horas, 'HOUR'))
            """)
    int crear(@Param("usuarioId") Long usuarioId, @Param("tipo") String tipo, @Param("hash") String hash,
            @Param("horas") int horas);

    /** Marca el enlace como usado solo si sigue vigente; devuelve 0 si ya se usó o venció (uso único y atómico). */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE {h-schema}TOKEN_ACCESO
               SET USADO_EN = CAST(SYSTIMESTAMP AS TIMESTAMP)
             WHERE ID = :id AND USADO_EN IS NULL AND EXPIRA_EN > CAST(SYSTIMESTAMP AS TIMESTAMP)
            """)
    int consumir(@Param("id") Long id);
}
