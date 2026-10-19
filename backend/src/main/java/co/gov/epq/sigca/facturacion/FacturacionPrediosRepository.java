package co.gov.epq.sigca.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.usuarios.Predio;

public interface FacturacionPrediosRepository extends JpaRepository<Predio, Long> {
}
