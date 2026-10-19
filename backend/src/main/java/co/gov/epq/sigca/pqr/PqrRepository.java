package co.gov.epq.sigca.pqr;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PqrRepository extends JpaRepository<Pqr, Long> {

    Optional<Pqr> findByNumeroRadicado(String numeroRadicado);

    Optional<Pqr> findByPqrOrigenId(Long pqrOrigenId);

    List<Pqr> findByUsuarioIdOrderByFechaRegistroDesc(Long usuarioId);

    List<Pqr> findByContratoIdAndEstadoOrderByFechaRespuestaDesc(Long contratoId, EstadoPqr estado);

    List<Pqr> findByPqrOrigenIdIn(Collection<Long> origenes);

    /** Abiertas cuya fecha límite ya pasó (job de vencimiento). */
    @Query("select p.id from Pqr p where p.estado in :estados and p.fechaLimiteRespuesta < :hoy order by p.id")
    List<Long> idsFueraDePlazo(Collection<EstadoPqr> estados, LocalDate hoy);

    /** Abiertas sin alerta enviada y con fecha límite hasta {@code hasta} (job de alerta previa). */
    @Query("""
            select p.id from Pqr p where p.estado in :estados and p.alerta48hEnviada = false
              and p.fechaLimiteRespuesta <= :hasta order by p.id""")
    List<Long> idsPorAlertar(Collection<EstadoPqr> estados, LocalDate hasta);

    @Query("""
            select p from Pqr p where p.silencioPositivo = true and p.fechaReconocimientoSilencio is null
            order by p.fechaLimiteReconocimiento""")
    List<Pqr> silenciosSinReconocer();
}
