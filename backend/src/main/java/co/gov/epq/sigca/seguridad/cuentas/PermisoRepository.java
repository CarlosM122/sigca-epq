package co.gov.epq.sigca.seguridad.cuentas;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    List<Permiso> findAllByOrderByModuloAscCodigoAsc();
}
