package co.gov.epq.sigca.pagos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    boolean existsByCodigoTransaccion(String codigoTransaccion);

    Optional<Pago> findByTransaccionPasarelaId(Long transaccionId);

    @Query("""
            select new co.gov.epq.sigca.pagos.PagoResumen(p.id, p.codigoPago, p.fechaPago, p.valor, p.canalPago,
                f.numeroFactura, a.numeroAcuerdo, q.numeroCuota, u.codigo, u.nombreCompleto, p.codigoTransaccion,
                p.referenciaBanco, p.estadoSyncErp)
            from Pago p
            join Usuario u on u.id = p.usuarioId
            left join Factura f on f.id = p.facturaId
            left join CuotaAcuerdo q on q.id = p.cuotaId
            left join AcuerdoPago a on a.id = q.acuerdoId
            where (:codigo is null or p.codigoPago = :codigo)
              and (:usuario is null or u.codigo = :usuario)
              and (:usuarioId is null or p.usuarioId = :usuarioId)
              and (:factura is null or f.numeroFactura = :factura)
              and (:canal is null or p.canalPago = :canal)
              and (:desde is null or p.fechaPago >= :desde)
              and (:hasta is null or p.fechaPago < :hasta)
            order by p.fechaPago desc, p.id desc""")
    Page<PagoResumen> buscar(@Param("codigo") String codigo, @Param("usuario") String usuario,
            @Param("usuarioId") Long usuarioId, @Param("factura") String factura, @Param("canal") CanalPago canal,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, Pageable pageable);

    @Query("""
            select p.id from Pago p
            where p.estadoSyncErp <> co.gov.epq.sigca.facturacion.EstadoSyncErp.SINCRONIZADA
            order by p.id""")
    List<Long> pendientesErp(Pageable pageable);
}
