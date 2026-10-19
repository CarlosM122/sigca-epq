package co.gov.epq.sigca.contratos;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Long>, JpaSpecificationExecutor<OrdenServicio> {

    /** RN-36: reconexiones pendientes cuyo plazo venció y que todavía no se avisaron a los asesores. */
    @Query("""
            select o from OrdenServicio o
            where o.tipo = co.gov.epq.sigca.contratos.TipoOrden.RECONEXION
              and o.estado = co.gov.epq.sigca.contratos.EstadoOrden.PENDIENTE
              and o.fechaLimite < :ahora
              and not exists (select 1 from Notificacion n
                              where n.evento = co.gov.epq.sigca.notificaciones.EventoNotificacion.RECONEXION_VENCIDA
                                and n.entidad = 'ORDEN_SERVICIO' and n.entidadId = o.id)
            order by o.fechaLimite
            """)
    List<OrdenServicio> reconexionesVencidasSinAviso(LocalDateTime ahora, Pageable pageable);

    /** Causa de la última suspensión ejecutada del contrato: explica por qué está suspendido. */
    @Query("""
            select o.causa from OrdenServicio o
            where o.contratoId = :contratoId
              and o.tipo = co.gov.epq.sigca.contratos.TipoOrden.SUSPENSION
              and o.estado = co.gov.epq.sigca.contratos.EstadoOrden.EJECUTADA
            order by o.fechaEjecucion desc, o.id desc
            """)
    List<CausaOrden> causasSuspensionEjecutada(Long contratoId, Pageable pageable);
}
