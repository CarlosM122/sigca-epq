package co.gov.epq.sigca.contratos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedidorRepository extends JpaRepository<Medidor, Long> {

    Optional<Medidor> findBySerial(String serial);

    boolean existsBySerial(String serial);

    List<Medidor> findByPredioIdOrderByEstadoAscFechaInstalacionDesc(Long predioId);

    Optional<Medidor> findFirstByPredioIdAndTipoServicioAndEstado(Long predioId, TipoServicio tipo, EstadoMedidor estado);
}
