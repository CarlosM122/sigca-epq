package co.gov.epq.sigca.usuarios;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.catalogo.Municipio;
import co.gov.epq.sigca.catalogo.MunicipioRepository;
import co.gov.epq.sigca.catalogo.Sector;
import co.gov.epq.sigca.catalogo.SectorRepository;
import co.gov.epq.sigca.common.error.NegocioException;

/**
 * Municipios y sectores para validar (RN-10) y para mostrar nombres en las respuestas.
 * Son pocos registros: se leen completos en cada uso para no guardar copias desactualizadas.
 */
@Component
@Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
public class CatalogoTerritorial {

    private final MunicipioRepository municipios;
    private final SectorRepository sectores;

    public CatalogoTerritorial(MunicipioRepository municipios, SectorRepository sectores) {
        this.municipios = municipios;
        this.sectores = sectores;
    }

    public Map<Long, String> nombresMunicipio() {
        return municipios.findAll().stream().collect(Collectors.toMap(Municipio::getId, Municipio::getNombre));
    }

    public Map<Long, String> nombresSector() {
        return sectores.findAll().stream().collect(Collectors.toMap(Sector::getId, Sector::getNombre));
    }

    public Map<Long, Municipio> municipiosPorId() {
        return municipios.findAll().stream().collect(Collectors.toMap(Municipio::getId, Function.identity()));
    }

    /** RN-10: el municipio existe y es atendido por EPQ. */
    public void exigirMunicipioActivo(Long municipioId) {
        Municipio m = municipios.findById(municipioId).orElse(null);
        if (m == null || !Boolean.TRUE.equals(m.getActivo())) {
            throw new NegocioException("MUNICIPIO_NO_ATENDIDO", "El municipio no es atendido por EPQ");
        }
    }

    /** P-02: el sector es del mismo municipio y está activo. */
    public void exigirSectorDelMunicipio(Long sectorId, Long municipioId) {
        Sector s = sectores.findById(sectorId).orElse(null);
        if (s == null || !s.getMunicipioId().equals(municipioId)) {
            throw new NegocioException("SECTOR_INVALIDO", "El sector no pertenece al municipio del predio", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        if (!Boolean.TRUE.equals(s.getActivo())) {
            throw new NegocioException("SECTOR_INVALIDO", "El sector no está activo");
        }
    }
}
