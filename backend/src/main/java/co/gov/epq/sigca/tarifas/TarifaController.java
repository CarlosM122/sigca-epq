package co.gov.epq.sigca.tarifas;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.tarifas.TarifaDtos.Cierre;
import co.gov.epq.sigca.tarifas.TarifaDtos.FactorDatos;
import co.gov.epq.sigca.tarifas.TarifaDtos.FactorVista;
import co.gov.epq.sigca.tarifas.TarifaDtos.NuevaVigencia;
import co.gov.epq.sigca.tarifas.TarifaDtos.TarifaDatos;
import co.gov.epq.sigca.tarifas.TarifaDtos.TarifaVista;
import co.gov.epq.sigca.usuarios.Paginas;
import co.gov.epq.sigca.usuarios.UsoPredio;
import jakarta.validation.Valid;

/** Tarifas (RF-22, P-08) y factores de subsidio o contribución (RN-37). Las tarifas no se editan: se cierran. */
@RestController
@RequestMapping("/api")
public class TarifaController {

    private static final String CONSULTA = "hasAnyAuthority('TARIFA_GESTIONAR','FACTURA_CONSULTAR')";
    private static final String GESTION = "hasAuthority('TARIFA_GESTIONAR')";

    private final TarifaService servicio;

    public TarifaController(TarifaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/tarifas")
    @PreAuthorize(CONSULTA)
    public Page<TarifaVista> tarifas(@RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) TipoServicio tipoServicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteEn,
            @PageableDefault(size = 50) Pageable pageable) {
        return servicio.buscarTarifas(municipioId, tipoServicio, vigenteEn, Paginas.limitar(pageable));
    }

    @PostMapping("/tarifas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    public TarifaVista crearTarifa(@Valid @RequestBody TarifaDatos req) {
        return servicio.crearTarifa(req);
    }

    @PostMapping("/tarifas/nueva-vigencia")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    public NuevaVigencia<TarifaVista> nuevaVigenciaTarifa(@Valid @RequestBody TarifaDatos req) {
        return servicio.nuevaVigenciaTarifa(req);
    }

    @PostMapping("/tarifas/{id}/cierre")
    @PreAuthorize(GESTION)
    public TarifaVista cerrarTarifa(@PathVariable Long id, @Valid @RequestBody Cierre req) {
        return servicio.cerrarTarifa(id, req);
    }

    @GetMapping("/factores-subsidio")
    @PreAuthorize(CONSULTA)
    public Page<FactorVista> factores(@RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) TipoServicio tipoServicio, @RequestParam(required = false) UsoPredio uso,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteEn,
            @PageableDefault(size = 50) Pageable pageable) {
        return servicio.buscarFactores(municipioId, tipoServicio, uso, vigenteEn, Paginas.limitar(pageable));
    }

    @PostMapping("/factores-subsidio")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    public FactorVista crearFactor(@Valid @RequestBody FactorDatos req) {
        return servicio.crearFactor(req);
    }

    @PostMapping("/factores-subsidio/nueva-vigencia")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    public NuevaVigencia<FactorVista> nuevaVigenciaFactor(@Valid @RequestBody FactorDatos req) {
        return servicio.nuevaVigenciaFactor(req);
    }

    @PostMapping("/factores-subsidio/{id}/cierre")
    @PreAuthorize(GESTION)
    public FactorVista cerrarFactor(@PathVariable Long id, @Valid @RequestBody Cierre req) {
        return servicio.cerrarFactor(id, req);
    }
}
