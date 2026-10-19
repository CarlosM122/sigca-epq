package co.gov.epq.sigca.pagos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.cartera.CuotaAcuerdo;

/** Cuotas de acuerdos de pago que se pagan (solo lectura desde pagos; los estados los cambia la BD). */
public interface PagosCuotasRepository extends JpaRepository<CuotaAcuerdo, Long> {

    List<CuotaAcuerdo> findByAcuerdoIdOrderByNumeroCuota(Long acuerdoId);

    Optional<CuotaAcuerdo> findByAcuerdoIdAndNumeroCuota(Long acuerdoId, Integer numeroCuota);
}
