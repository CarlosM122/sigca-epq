package co.gov.epq.sigca.cartera;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.contratos.OrdenServicio;

/** Órdenes de suspensión por mora que crea cartera (P-11). Repositorio propio para no depender del módulo de contratos. */
public interface CarteraOrdenRepository extends JpaRepository<OrdenServicio, Long> {
}
