package co.gov.epq.sigca.facturacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

    Optional<Factura> findByNumeroFactura(String numeroFactura);

    Optional<Factura> findByReexpedicionDeId(Long reexpedicionDeId);

    @Query("""
            select new co.gov.epq.sigca.facturacion.FacturaResumen(f.id, f.numeroFactura, f.periodo, u.codigo,
                u.nombreCompleto, p.codigoPredio, p.direccion, m.nombre, f.fechaEmision, f.fechaVencimiento,
                f.totalAPagar, f.saldoPendiente, f.valorEnReclamo, f.estado, f.reexpedicionDeId)
            from Factura f
            join Usuario u on u.id = f.usuarioId
            join Predio p on p.id = f.predioId
            join Municipio m on m.id = p.municipioId
            where (:numero is null or f.numeroFactura = :numero)
              and (:usuario is null or u.codigo = :usuario)
              and (:predio is null or p.codigoPredio = :predio)
              and (:periodo is null or f.periodo = :periodo)
              and (:estado is null or f.estado = :estado)
              and (:municipioId is null or p.municipioId = :municipioId)
              and (:loteId is null or f.loteId = :loteId)
            order by f.periodo desc, f.id desc""")
    Page<FacturaResumen> buscar(@Param("numero") String numero, @Param("usuario") String usuario,
            @Param("predio") String predio, @Param("periodo") Integer periodo, @Param("estado") EstadoFactura estado,
            @Param("municipioId") Long municipioId, @Param("loteId") Long loteId, Pageable pageable);

    /** Facturas de un titular desde un periodo (portal, RF-07). */
    List<Factura> findByUsuarioIdAndPeriodoGreaterThanEqualOrderByPeriodoDescIdDesc(Long usuarioId, Integer periodo);

    boolean existsByUsuarioIdAndPeriodoAndEstadoNot(Long usuarioId, Integer periodo, EstadoFactura estado);

    /** RF-21: PENDIENTES con vencimiento cumplido y valor exigible (lo reclamado no vence, RN-33). */
    @Query("""
            select f.id from Factura f
            where f.estado = co.gov.epq.sigca.facturacion.EstadoFactura.PENDIENTE
              and f.fechaVencimiento < :hoy and f.saldoPendiente - f.valorEnReclamo > 0
            order by f.id""")
    List<Long> porVencer(@Param("hoy") LocalDate hoy);

    @Query("""
            select f.id from Factura f
            where f.estadoSyncErp <> co.gov.epq.sigca.facturacion.EstadoSyncErp.SINCRONIZADA
            order by f.id""")
    List<Long> pendientesErp(Pageable pageable);
}
