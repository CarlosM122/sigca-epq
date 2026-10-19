package co.gov.epq.sigca.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorRepository extends JpaRepository<Sector, Long> {

    List<Sector> findByMunicipioIdOrderByNombre(Long municipioId);

    List<Sector> findByMunicipioIdAndActivoTrueOrderByNombre(Long municipioId);
}
