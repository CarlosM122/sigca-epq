package co.gov.epq.sigca.cartera;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarteraAcuerdoFacturaRepository extends JpaRepository<AcuerdoFactura, AcuerdoFactura.Clave> {
}
