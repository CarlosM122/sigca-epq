package co.gov.epq.sigca.contratos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContratoServicioRepository extends JpaRepository<ContratoServicio, Long>,
        JpaSpecificationExecutor<ContratoServicio> {

    List<ContratoServicio> findByUsuarioIdOrderByNumeroContrato(Long usuarioId);

    List<ContratoServicio> findByPredioIdOrderByNumeroContrato(Long predioId);

    boolean existsByPredioIdAndTipoServicioAndEstadoIn(Long predioId, TipoServicio tipo, Collection<EstadoContrato> estados);

    boolean existsByMedidorIdAndEstadoIn(Long medidorId, Collection<EstadoContrato> estados);

    Optional<ContratoServicio> findFirstByMedidorIdAndEstadoIn(Long medidorId, Collection<EstadoContrato> estados);

    List<ContratoServicio> findByMedidorIdInAndEstadoIn(Collection<Long> medidorIds, Collection<EstadoContrato> estados);

    Optional<ContratoServicio> findFirstByPredioIdAndTipoServicioAndEstadoIn(Long predioId, TipoServicio tipo,
            Collection<EstadoContrato> estados);
}
