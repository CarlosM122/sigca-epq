package co.gov.epq.sigca.pqr;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PqrActuacionRepository extends JpaRepository<PqrActuacion, Long> {

    List<PqrActuacion> findByPqrIdOrderByFechaAscIdAsc(Long pqrId);
}
