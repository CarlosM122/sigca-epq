package co.gov.epq.sigca.notificaciones;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    @Query("""
            select n from Notificacion n
            where n.estado = co.gov.epq.sigca.notificaciones.EstadoNotificacion.PENDIENTE
              and n.canal <> co.gov.epq.sigca.notificaciones.CanalNotificacion.SISTEMA
              and n.proximoIntento <= :ahora
            order by n.id
            """)
    List<Notificacion> pendientesDeEnvio(LocalDateTime ahora, Pageable pageable);

    List<Notificacion> findByCanalAndFuncionarioIdOrderByIdDesc(CanalNotificacion canal, Long funcionarioId, Pageable pageable);

    long countByCanalAndFuncionarioIdAndLeidaFalse(CanalNotificacion canal, Long funcionarioId);
}
