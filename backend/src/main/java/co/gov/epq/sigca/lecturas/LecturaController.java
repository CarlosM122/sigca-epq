package co.gov.epq.sigca.lecturas;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.gov.epq.sigca.lecturas.LecturaDtos.LecturaRegistrar;
import co.gov.epq.sigca.lecturas.LecturaDtos.LecturaVista;
import co.gov.epq.sigca.lecturas.LecturaDtos.ResultadoCarga;
import co.gov.epq.sigca.lecturas.LecturaDtos.Revision;
import co.gov.epq.sigca.usuarios.Paginas;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Lecturas: registro manual, cargue CSV, crítica y su revisión (P-07, RN-35). */
@RestController
@RequestMapping("/api/lecturas")
@Validated
public class LecturaController {

    private final LecturaService servicio;
    private final CargaLecturasService carga;

    public LecturaController(LecturaService servicio, CargaLecturasService carga) {
        this.servicio = servicio;
        this.carga = carga;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LECTURA_REGISTRAR')")
    public LecturaVista registrar(@Valid @RequestBody LecturaRegistrar req) {
        return servicio.registrar(req);
    }

    /** Resultado por fila; la respuesta es 200 aunque haya filas con error. */
    @PostMapping(path = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('LECTURA_CARGAR_ARCHIVO')")
    public ResultadoCarga cargar(@RequestPart("archivo") MultipartFile archivo) {
        return carga.cargar(archivo);
    }

    @GetMapping("/revision")
    @PreAuthorize("hasAuthority('LECTURA_REVISAR')")
    public Page<LecturaVista> enRevision(@RequestParam(required = false) Long municipioId,
            @PageableDefault(size = 20) Pageable pageable) {
        return servicio.enRevision(municipioId, Paginas.limitar(pageable));
    }

    @PostMapping("/{id}/revision")
    @PreAuthorize("hasAuthority('LECTURA_REVISAR')")
    public LecturaVista revisar(@PathVariable Long id, @Valid @RequestBody Revision req) {
        return servicio.revisar(id, req);
    }

    /** Historial por medidor (id o serial). */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('LECTURA_REGISTRAR','LECTURA_REVISAR','CONTRATO_CONSULTAR')")
    public Page<LecturaVista> historial(@RequestParam(required = false) Long medidorId,
            @RequestParam(required = false) @Size(max = 30) String serial, @PageableDefault(size = 24) Pageable pageable) {
        return servicio.historial(medidorId, serial, Paginas.limitar(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('LECTURA_REGISTRAR','LECTURA_REVISAR','CONTRATO_CONSULTAR')")
    public LecturaVista consultar(@PathVariable Long id) {
        return servicio.consultar(id);
    }
}
