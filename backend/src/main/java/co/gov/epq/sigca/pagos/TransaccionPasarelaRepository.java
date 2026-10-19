package co.gov.epq.sigca.pagos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TransaccionPasarelaRepository extends JpaRepository<TransaccionPasarela, Long> {

    Optional<TransaccionPasarela> findByReferencia(String referencia);

    /** Bloquea la fila: dos notificaciones simultáneas de la misma transacción se procesan en serie. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransaccionPasarela t where t.referencia = :referencia")
    Optional<TransaccionPasarela> bloquearPorReferencia(@Param("referencia") String referencia);

    Optional<TransaccionPasarela> findFirstByFacturaIdAndEstado(Long facturaId, EstadoTransaccion estado);

    Optional<TransaccionPasarela> findFirstByCuotaIdAndEstado(Long cuotaId, EstadoTransaccion estado);

    /**
     * Expira los intentos INICIADOS sin respuesta. Se compara con SYSTIMESTAMP porque INICIADA_EN lo asigna
     * el trigger con el reloj de la BD.
     */
    @Modifying
    @Query(value = """
            UPDATE {h-schema}TRANSACCION_PASARELA
               SET ESTADO = 'EXPIRADA', MENSAJE_RESPUESTA = 'Sin respuesta de la pasarela', VERSION = VERSION + 1
             WHERE ESTADO = 'INICIADA'
               AND INICIADA_EN < CAST(SYSTIMESTAMP AS TIMESTAMP) - NUMTODSINTERVAL(:minutos, 'MINUTE')""",
            nativeQuery = true)
    int expirarAnteriores(@Param("minutos") int minutos);
}
