package co.gov.epq.sigca.contratos;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.contratos.ContratoDtos.Cancelacion;
import co.gov.epq.sigca.contratos.ContratoDtos.ContratoCrear;
import co.gov.epq.sigca.contratos.ContratoDtos.ContratoVista;
import co.gov.epq.sigca.usuarios.Paginas;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** CU-02 Gestionar contrato (RF-04, RF-17). Suspender y reconectar se hace con órdenes ({@link OrdenController}). */
@RestController
@RequestMapping("/api")
@Validated
public class ContratoController {

    private final ContratoService servicio;

    public ContratoController(ContratoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/contratos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTRATO_CREAR')")
    public ContratoVista crear(@Valid @RequestBody ContratoCrear req) {
        return servicio.crear(req);
    }

    @GetMapping("/contratos")
    @PreAuthorize("hasAuthority('CONTRATO_CONSULTAR')")
    public Page<ContratoVista> buscar(@RequestParam(required = false) @Size(max = 12) String numero,
            @RequestParam(required = false) Long usuarioId, @RequestParam(required = false) Long predioId,
            @RequestParam(required = false) Long municipioId, @RequestParam(required = false) TipoServicio tipoServicio,
            @RequestParam(required = false) EstadoContrato estado, @PageableDefault(size = 20) Pageable pageable) {
        return servicio.buscar(numero, usuarioId, predioId, municipioId, tipoServicio, estado, Paginas.limitar(pageable));
    }

    @GetMapping("/contratos/{id}")
    @PreAuthorize("hasAuthority('CONTRATO_CONSULTAR')")
    public ContratoVista consultar(@PathVariable Long id) {
        return servicio.consultar(id);
    }

    @GetMapping("/usuarios/{usuarioId}/contratos")
    @PreAuthorize("hasAnyAuthority('CONTRATO_CONSULTAR','USUARIO_CONSULTAR')")
    public List<ContratoVista> deUsuario(@PathVariable Long usuarioId) {
        return servicio.deUsuario(usuarioId);
    }

    @PostMapping("/contratos/{id}/cancelacion")
    @PreAuthorize("hasAuthority('CONTRATO_CANCELAR')")
    public ContratoVista cancelar(@PathVariable Long id, @Valid @RequestBody Cancelacion req) {
        return servicio.cancelar(id, req);
    }
}
