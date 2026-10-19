package co.gov.epq.sigca.usuarios;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.usuarios.PredioDtos.PredioDatos;
import co.gov.epq.sigca.usuarios.PredioDtos.PredioDetalle;
import co.gov.epq.sigca.usuarios.PredioDtos.PredioVista;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** RF-18 predios (P-06). Los medidores del predio están en {@code MedidorController}. */
@RestController
@RequestMapping("/api/predios")
@Validated
public class PredioController {

    private static final String CONSULTA = "hasAnyAuthority('PREDIO_GESTIONAR','USUARIO_CONSULTAR','CONTRATO_CONSULTAR')";

    private final PredioService servicio;

    public PredioController(PredioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize(CONSULTA)
    public Page<PredioVista> buscar(@RequestParam(required = false) @Size(max = 100) String texto,
            @RequestParam(required = false) Long municipioId, @PageableDefault(size = 20) Pageable pageable) {
        return servicio.buscar(texto, municipioId, Paginas.limitar(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    public PredioDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PREDIO_GESTIONAR')")
    public PredioVista crear(@Valid @RequestBody PredioDatos req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PREDIO_GESTIONAR')")
    public PredioVista modificar(@PathVariable Long id, @Valid @RequestBody PredioDatos req) {
        return servicio.modificar(id, req);
    }
}
