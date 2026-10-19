package co.gov.epq.sigca.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroSistemaRepository extends JpaRepository<ParametroSistema, String> {

    List<ParametroSistema> findAllByOrderByClave();
}
