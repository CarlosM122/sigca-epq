package co.gov.epq.sigca.contratos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.contratos.ContratoDtos.OrdenAnular;
import co.gov.epq.sigca.contratos.ContratoDtos.OrdenCrear;
import co.gov.epq.sigca.contratos.ContratoDtos.OrdenEjecutar;
import co.gov.epq.sigca.contratos.ContratoDtos.OrdenVista;
import co.gov.epq.sigca.usuarios.Paginas;
import jakarta.validation.Valid;

/** Bandeja y gestión de órdenes de suspensión y reconexión (P-11, RN-36). */
@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    private final OrdenService servicio;

    public OrdenController(OrdenService servicio) {
        this.servicio = servicio;
    }

    /**
     * Sin estado ni contrato se listan las PENDIENTES (bandeja); con contrato y sin estado, todo su historial.
     * {@code vencidas=true} deja solo las pendientes que pasaron su FECHA_LIMITE.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDEN_GESTIONAR','CONTRATO_CONSULTAR')")
    public Page<OrdenVista> buscar(@RequestParam(required = false) EstadoOrden estado,
            @RequestParam(required = false) TipoOrden tipo, @RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) Long contratoId, @RequestParam(defaultValue = "false") boolean vencidas,
            @PageableDefault(size = 20) Pageable pageable) {
        return servicio.buscar(estado == null && contratoId == null ? EstadoOrden.PENDIENTE : estado, tipo, municipioId, contratoId, vencidas, Paginas.limitar(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ORDEN_GESTIONAR','CONTRATO_CONSULTAR')")
    public OrdenVista consultar(@PathVariable Long id) {
        return servicio.consultar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORDEN_GESTIONAR')")
    public OrdenVista crear(@Valid @RequestBody OrdenCrear req) {
        return servicio.crear(req);
    }

    @PostMapping("/{id}/ejecucion")
    @PreAuthorize("hasAuthority('ORDEN_GESTIONAR')")
    public OrdenVista ejecutar(@PathVariable Long id, @Valid @RequestBody(required = false) OrdenEjecutar req) {
        return servicio.ejecutar(id, req == null ? new OrdenEjecutar(null, null) : req);
    }

    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAuthority('ORDEN_GESTIONAR')")
    public OrdenVista anular(@PathVariable Long id, @Valid @RequestBody OrdenAnular req) {
        return servicio.anular(id, req);
    }
}
