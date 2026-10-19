package co.gov.epq.sigca.facturacion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.gov.epq.sigca.contratos.ContratoServicio;
import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.contratos.TipoServicio;

/** Contratos que factura el lote (solo lectura desde facturación). */
public interface FacturacionContratosRepository extends JpaRepository<ContratoServicio, Long> {

    /**
     * Pares titular-predio con al menos un contrato ACTIVO vinculado antes del cierre del periodo y sin
     * factura vigente del periodo: lo que falta facturar (relanzar el lote no duplica).
     */
    @Query("""
            select distinct new co.gov.epq.sigca.facturacion.ClaveFactura(c.usuarioId, c.predioId)
            from ContratoServicio c
            where c.estado = co.gov.epq.sigca.contratos.EstadoContrato.ACTIVO
              and c.fechaVinculacion <= :fin
              and not exists (select 1 from Factura f
                               where f.usuarioId = c.usuarioId and f.predioId = c.predioId and f.periodo = :periodo
                                 and f.estado <> co.gov.epq.sigca.facturacion.EstadoFactura.ANULADA)""")
    List<ClaveFactura> pendientesDeFacturar(@Param("periodo") Integer periodo, @Param("fin") LocalDate fin);

    List<ContratoServicio> findByUsuarioIdAndPredioIdAndEstadoOrderByTipoServicio(Long usuarioId, Long predioId,
            EstadoContrato estado);

    List<ContratoServicio> findByPredioIdAndTipoServicioAndEstadoIn(Long predioId, TipoServicio tipo,
            List<EstadoContrato> estados);
}
