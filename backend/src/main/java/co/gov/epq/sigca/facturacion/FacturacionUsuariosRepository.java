package co.gov.epq.sigca.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import co.gov.epq.sigca.usuarios.Usuario;

public interface FacturacionUsuariosRepository extends JpaRepository<Usuario, Long> {
}
