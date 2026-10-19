package co.gov.epq.sigca.portal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.contratos.ContratoServicio;
import co.gov.epq.sigca.contratos.EstadoContrato;

/** Contratos vistos desde el portal: siempre filtrados por el titular autenticado. */
public interface PortalContratosRepository extends JpaRepository<ContratoServicio, Long> {

    List<ContratoServicio> findByUsuarioIdAndEstadoInOrderByPredioIdAscTipoServicioAsc(Long usuarioId,
            Collection<EstadoContrato> estados);

    Optional<ContratoServicio> findByIdAndUsuarioId(Long id, Long usuarioId);
}
