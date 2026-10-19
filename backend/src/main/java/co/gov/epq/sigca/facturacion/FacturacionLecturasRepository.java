package co.gov.epq.sigca.facturacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.lecturas.EstadoLectura;
import co.gov.epq.sigca.lecturas.Lectura;

/** Lecturas que liquida facturación y que muestra el portal (solo lectura). */
public interface FacturacionLecturasRepository extends JpaRepository<Lectura, Long> {

    Optional<Lectura> findByMedidorIdAndPeriodo(Long medidorId, Integer periodo);

    List<Lectura> findByMedidorIdInAndPeriodoGreaterThanEqualAndEstadoInOrderByPeriodoDesc(Collection<Long> medidores,
            Integer desde, Collection<EstadoLectura> estados);
}
