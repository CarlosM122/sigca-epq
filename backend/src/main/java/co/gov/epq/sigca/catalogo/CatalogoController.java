package co.gov.epq.sigca.catalogo;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogos públicos que necesitan los formularios (portal sin sesión incluido).
 */
@RestController
@RequestMapping("/api/publico")
@Transactional(readOnly = true)
public class CatalogoController {

    private final MunicipioRepository municipios;
    private final SectorRepository sectores;

    public CatalogoController(MunicipioRepository municipios, SectorRepository sectores) {
        this.municipios = municipios;
        this.sectores = sectores;
    }

    @GetMapping("/municipios")
    public List<MunicipioView> municipios() {
        return municipios.findByActivoTrueOrderByNombre().stream()
                .map(m -> new MunicipioView(m.getId(), m.getCodigoDane(), m.getNombre()))
                .toList();
    }

    @GetMapping("/municipios/{id}/sectores")
    public List<SectorView> sectores(@PathVariable Long id) {
        return sectores.findByMunicipioIdAndActivoTrueOrderByNombre(id).stream()
                .map(s -> new SectorView(s.getId(), s.getNombre(), s.getTipo()))
                .toList();
    }

    public record MunicipioView(Long id, String codigoDane, String nombre) {
    }

    public record SectorView(Long id, String nombre, TipoSector tipo) {
    }
}
