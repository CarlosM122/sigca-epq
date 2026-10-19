package co.gov.epq.sigca.cartera;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CarteraCuotaRepository extends JpaRepository<CuotaAcuerdo, Long> {

    List<CuotaAcuerdo> findByAcuerdoIdOrderByNumeroCuota(Long acuerdoId);

    /** Acuerdos vigentes con alguna cuota pendiente que venció antes de {@code limite}. */
    @Query("""
            select distinct c.acuerdoId from CuotaAcuerdo c, AcuerdoPago a
             where a.id = c.acuerdoId and a.estado = co.gov.epq.sigca.cartera.EstadoAcuerdo.VIGENTE
               and c.estado = co.gov.epq.sigca.cartera.EstadoCuota.PENDIENTE and c.fechaVencimiento < :limite
             order by c.acuerdoId""")
    List<Long> acuerdosConCuotasVencidas(LocalDate limite);

    @Query("""
            select c from CuotaAcuerdo c where c.acuerdoId = :acuerdoId
               and c.estado = co.gov.epq.sigca.cartera.EstadoCuota.PENDIENTE and c.fechaVencimiento < :limite
             order by c.numeroCuota""")
    List<CuotaAcuerdo> cuotasVencidas(Long acuerdoId, LocalDate limite);
}
