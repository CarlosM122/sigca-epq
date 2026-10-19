package co.gov.epq.sigca.facturacion;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteFacturacionRepository extends JpaRepository<LoteFacturacion, Long> {

    boolean existsByPeriodoAndEstadoIn(Integer periodo, List<EstadoLote> estados);

    List<LoteFacturacion> findAllByOrderByIdDesc(Pageable pageable);
}
