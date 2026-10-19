package co.gov.epq.sigca.pagos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.gov.epq.sigca.cartera.AcuerdoPago;

public interface PagosAcuerdosRepository extends JpaRepository<AcuerdoPago, Long> {

    Optional<AcuerdoPago> findByNumeroAcuerdo(String numeroAcuerdo);

    List<AcuerdoPago> findByUsuarioIdOrderByFechaAcuerdoDescIdDesc(Long usuarioId);

    /** Acuerdo que refinancia una factura EN_ACUERDO, para orientar al cajero. */
    @Query("""
            select a.numeroAcuerdo from AcuerdoPago a join AcuerdoFactura af on af.acuerdoId = a.id
            where af.facturaId = :facturaId order by a.id desc""")
    List<String> numerosPorFactura(@Param("facturaId") Long facturaId);
}
