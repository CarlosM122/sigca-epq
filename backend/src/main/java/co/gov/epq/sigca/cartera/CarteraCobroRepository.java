package co.gov.epq.sigca.cartera;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarteraCobroRepository extends JpaRepository<CobroPersuasivo, Long> {

    Optional<CobroPersuasivo> findFirstByUsuarioIdAndEstadoIn(Long usuarioId, Collection<EstadoCobro> estados);
}
