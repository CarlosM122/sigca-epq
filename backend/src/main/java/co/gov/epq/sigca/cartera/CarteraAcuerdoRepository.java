package co.gov.epq.sigca.cartera;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarteraAcuerdoRepository extends JpaRepository<AcuerdoPago, Long> {

    boolean existsByUsuarioIdAndEstado(Long usuarioId, EstadoAcuerdo estado);
}
