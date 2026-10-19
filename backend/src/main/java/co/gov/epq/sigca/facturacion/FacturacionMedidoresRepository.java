package co.gov.epq.sigca.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.contratos.Medidor;

public interface FacturacionMedidoresRepository extends JpaRepository<Medidor, Long> {
}
