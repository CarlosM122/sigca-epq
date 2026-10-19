package co.gov.epq.sigca.portal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.lecturas.Lectura;

/** Historial de lecturas de un medidor para el portal. */
public interface PortalLecturasRepository extends JpaRepository<Lectura, Long> {

    List<Lectura> findByMedidorIdAndPeriodoGreaterThanEqualOrderByPeriodoDesc(Long medidorId, Integer periodo);
}
