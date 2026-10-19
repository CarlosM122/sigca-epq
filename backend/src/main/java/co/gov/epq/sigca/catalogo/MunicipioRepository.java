package co.gov.epq.sigca.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipioRepository extends JpaRepository<Municipio, Long> {

    List<Municipio> findByActivoTrueOrderByNombre();

    List<Municipio> findAllByOrderByNombre();
}
