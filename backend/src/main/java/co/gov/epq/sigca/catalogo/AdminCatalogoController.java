package co.gov.epq.sigca.catalogo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.catalogo.AdminCatalogoService.FestivoVista;
import co.gov.epq.sigca.catalogo.AdminCatalogoService.MunicipioVista;
import co.gov.epq.sigca.catalogo.AdminCatalogoService.ParametroVista;
import co.gov.epq.sigca.catalogo.AdminCatalogoService.SectorVista;
import co.gov.epq.sigca.common.validation.SinHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Administración de parámetros, festivos, municipios y sectores (RF-29). Permiso PARAMETRO_GESTIONAR.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('PARAMETRO_GESTIONAR')")
public class AdminCatalogoController {

    private final AdminCatalogoService servicio;

    public AdminCatalogoController(AdminCatalogoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/parametros")
    public List<ParametroVista> parametros() {
        return servicio.parametros();
    }

    @PutMapping("/parametros/{clave}")
    public ParametroVista modificarParametro(@PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,59}$") String clave,
            @Valid @RequestBody ValorParametro datos) {
        return servicio.modificarParametro(clave, datos.valor());
    }

    @GetMapping("/festivos")
    public List<FestivoVista> festivos(@RequestParam @Min(2000) @Max(2100) int anio) {
        return servicio.festivos(anio);
    }

    @PostMapping("/festivos")
    @ResponseStatus(HttpStatus.CREATED)
    public FestivoVista agregarFestivo(@Valid @RequestBody NuevoFestivo datos) {
        return servicio.agregarFestivo(datos.fecha(), datos.descripcion());
    }

    @DeleteMapping("/festivos/{fecha}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarFestivo(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        servicio.eliminarFestivo(fecha);
    }

    @GetMapping("/municipios")
    public List<MunicipioVista> municipios() {
        return servicio.municipios();
    }

    @PutMapping("/municipios/{id}/estado")
    public MunicipioVista estadoMunicipio(@PathVariable Long id, @Valid @RequestBody Estado datos) {
        return servicio.cambiarEstadoMunicipio(id, datos.activo());
    }

    @GetMapping("/municipios/{id}/sectores")
    public List<SectorVista> sectores(@PathVariable Long id) {
        return servicio.sectores(id);
    }

    @PostMapping("/municipios/{id}/sectores")
    @ResponseStatus(HttpStatus.CREATED)
    public SectorVista crearSector(@PathVariable Long id, @Valid @RequestBody DatosSector datos) {
        return servicio.crearSector(id, datos.nombre(), datos.tipo());
    }

    @PutMapping("/sectores/{id}")
    public SectorVista modificarSector(@PathVariable Long id, @Valid @RequestBody DatosSector datos) {
        return servicio.modificarSector(id, datos.nombre(), datos.tipo());
    }

    @PutMapping("/sectores/{id}/estado")
    public SectorVista estadoSector(@PathVariable Long id, @Valid @RequestBody Estado datos) {
        return servicio.cambiarEstadoSector(id, datos.activo());
    }

    public record ValorParametro(@NotBlank @Size(max = 400) @SinHtml String valor) {
    }

    public record NuevoFestivo(@NotNull LocalDate fecha, @NotBlank @Size(min = 3, max = 100) @SinHtml String descripcion) {
    }

    public record DatosSector(@NotBlank @Size(min = 3, max = 80) @SinHtml String nombre, @NotNull TipoSector tipo) {
    }

    public record Estado(@NotNull Boolean activo) {
    }
}
