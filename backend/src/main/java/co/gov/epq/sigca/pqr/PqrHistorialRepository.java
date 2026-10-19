package co.gov.epq.sigca.pqr;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PqrHistorialRepository extends JpaRepository<PqrHistorial, Long> {

    List<PqrHistorial> findByPqrIdOrderByFechaAscIdAsc(Long pqrId);
}
