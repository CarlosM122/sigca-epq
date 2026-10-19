package co.gov.epq.sigca.lecturas;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LecturaRepository extends JpaRepository<Lectura, Long>, JpaSpecificationExecutor<Lectura> {

    Page<Lectura> findByMedidorIdOrderByPeriodoDesc(Long medidorId, Pageable pageable);
}
