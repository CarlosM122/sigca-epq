package co.gov.epq.sigca.facturacion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LineaFacturaRepository extends JpaRepository<LineaFactura, Long> {

    @Query("""
            select new co.gov.epq.sigca.facturacion.LineaDetalle(l.id, l.contratoId, c.numeroContrato, l.tipoServicio,
                lec.periodo, lec.lecturaAnterior, lec.lecturaActual, l.consumo, l.consumoBasico, l.consumoComplementario,
                l.consumoSuntuario, l.consumoEstimado, l.cargoFijo, l.valorConsumo, l.subsidio, l.contribucion,
                l.otrosCargos, l.detalleOtrosCargos, l.interesesMora, l.totalLinea, l.valorAjuste, l.estado, l.enReclamo)
            from LineaFactura l
            join ContratoServicio c on c.id = l.contratoId
            join Lectura lec on lec.id = l.lecturaId
            where l.facturaId = :facturaId
            order by l.tipoServicio, l.id""")
    List<LineaDetalle> detalle(@Param("facturaId") Long facturaId);
}
