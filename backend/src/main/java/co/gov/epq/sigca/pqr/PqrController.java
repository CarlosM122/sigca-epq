package co.gov.epq.sigca.pqr;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * PQR para funcionarios: radicación en ventanilla (P-15), bandeja y trámite (RF-09, RF-11, RF-12, RF-13, P-03).
 * El acceso por municipio (RN-30) lo aplica {@link TramitePqrService}.
 */
@RestController
@RequestMapping("/api/pqr")
@Validated
public class PqrController {

    private final RadicacionPqrService radicacion;
    private final TramitePqrService tramite;

    public PqrController(RadicacionPqrService radicacion, TramitePqrService tramite) {
        this.radicacion = radicacion;
        this.tramite = tramite;
    }

    // ---------------------------------------------------------------- ventanilla

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PQR_REGISTRAR_PRESENCIAL')")
    public PqrVistas.Radicada radicarPresencial(@RequestPart("datos") @Valid PqrSolicitudes.Radicar datos,
            @RequestPart(name = "adjuntos", required = false) List<MultipartFile> adjuntos) {
        return radicacion.radicarPresencial(datos, PqrHttp.entradas(adjuntos));
    }

    @GetMapping("/contratos/{numero}")
    @PreAuthorize("hasAuthority('PQR_REGISTRAR_PRESENCIAL')")
    public PqrVistas.ContratoPqr contrato(@PathVariable @Pattern(regexp = "CTR-\\d{8}", message = "Número de contrato inválido") String numero) {
        return radicacion.contratoParaRadicar(numero);
    }

    // ---------------------------------------------------------------- consulta

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PQR_CONSULTAR','PQR_TRAMITAR','PQR_CONSULTAR_TODAS')")
    public Page<PqrVistas.Resumen> bandeja(@RequestParam(required = false) EstadoPqr estado,
            @RequestParam(required = false) TipoPqr tipo, @RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) @Pattern(regexp = "[A-Za-z0-9-]{1,30}", message = "Radicado inválido") String radicado,
            @RequestParam(required = false) Long gestorId,
            @RequestParam(required = false) @Pattern(regexp = "POR_VENCER|VENCIDAS|SILENCIO", message = "Filtro de vencimiento inválido") String vencimiento,
            @PageableDefault(size = 20, sort = "fechaLimiteRespuesta", direction = Sort.Direction.ASC) Pageable pageable) {
        return tramite.bandeja(new TramitePqrService.Filtro(estado, tipo, municipioId, radicado, null, gestorId, vencimiento),
                pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PQR_CONSULTAR','PQR_TRAMITAR','PQR_CONSULTAR_TODAS')")
    public PqrVistas.Detalle detalle(@PathVariable Long id) {
        return tramite.detalle(id);
    }

    @GetMapping("/{id}/adjuntos/{adjuntoId}")
    @PreAuthorize("hasAnyAuthority('PQR_CONSULTAR','PQR_TRAMITAR','PQR_CONSULTAR_TODAS')")
    public ResponseEntity<byte[]> adjunto(@PathVariable Long id, @PathVariable Long adjuntoId) {
        return PqrHttp.descarga(tramite.adjunto(id, adjuntoId));
    }

    @GetMapping("/gestores")
    @PreAuthorize("hasAuthority('PQR_REASIGNAR')")
    public List<PqrVistas.Gestor> gestores(@RequestParam(required = false) Long municipioId) {
        return tramite.gestores(municipioId);
    }

    // ---------------------------------------------------------------- trámite

    @PostMapping("/{id}/tomar")
    @PreAuthorize("hasAuthority('PQR_TRAMITAR')")
    public PqrVistas.Detalle tomar(@PathVariable Long id) {
        return tramite.tomar(id);
    }

    @PostMapping("/{id}/actuaciones")
    @PreAuthorize("hasAuthority('PQR_TRAMITAR')")
    public PqrVistas.Detalle actuacion(@PathVariable Long id, @RequestBody @Valid PqrSolicitudes.Actuacion a) {
        return tramite.registrarActuacion(id, a);
    }

    @PostMapping(path = "/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PQR_TRAMITAR')")
    public PqrVistas.Detalle evidencias(@PathVariable Long id, @RequestPart("archivos") List<MultipartFile> archivos) {
        return tramite.adjuntarEvidencias(id, PqrHttp.entradas(archivos));
    }

    @PostMapping("/{id}/ajuste-linea")
    @PreAuthorize("hasAuthority('PQR_TRAMITAR')")
    public PqrVistas.Detalle ajusteLinea(@PathVariable Long id, @RequestBody @Valid PqrSolicitudes.AjusteLinea a) {
        return tramite.ajustarLinea(id, a);
    }

    @PostMapping("/{id}/respuesta")
    @PreAuthorize("hasAuthority('PQR_TRAMITAR')")
    public PqrVistas.Detalle responder(@PathVariable Long id, @RequestBody @Valid PqrSolicitudes.Respuesta r) {
        return tramite.responder(id, r);
    }

    @PostMapping("/{id}/traslado-sspd")
    @PreAuthorize("hasAuthority('PQR_TRAMITAR')")
    public PqrVistas.Detalle trasladar(@PathVariable Long id, @RequestBody @Valid PqrSolicitudes.Traslado t) {
        return tramite.trasladarSspd(id, t);
    }

    @PostMapping("/{id}/decision-sspd")
    @PreAuthorize("hasAuthority('PQR_TRAMITAR')")
    public PqrVistas.Detalle decisionSspd(@PathVariable Long id, @RequestBody @Valid PqrSolicitudes.DecisionSspd d) {
        return tramite.registrarDecisionSspd(id, d);
    }

    @PostMapping("/{id}/reasignacion")
    @PreAuthorize("hasAuthority('PQR_REASIGNAR')")
    public PqrVistas.Detalle reasignar(@PathVariable Long id, @RequestBody @Valid PqrSolicitudes.Reasignacion r) {
        return tramite.reasignar(id, r);
    }

    @PostMapping("/{id}/reconocimiento-silencio")
    @PreAuthorize("hasAuthority('PQR_RECONOCER_SILENCIO')")
    public PqrVistas.Detalle reconocerSilencio(@PathVariable Long id, @RequestBody @Valid PqrSolicitudes.Reconocimiento r) {
        return tramite.reconocerSilencio(id, r);
    }
}
