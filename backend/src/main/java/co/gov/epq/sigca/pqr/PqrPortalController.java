package co.gov.epq.sigca.pqr;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

/** PQR del ciudadano autenticado (P-14 canal PORTAL, RF-24). Todo se filtra por el usuario del token. */
@RestController
@RequestMapping("/api/portal/pqr")
@PreAuthorize("hasAuthority('PORTAL_PQR')")
public class PqrPortalController {

    private final RadicacionPqrService radicacion;

    public PqrPortalController(RadicacionPqrService radicacion) {
        this.radicacion = radicacion;
    }

    @GetMapping
    public List<PqrVistas.Portal> mias() {
        return radicacion.misPqr();
    }

    @GetMapping("/contexto")
    public PqrVistas.ContextoPortal contexto() {
        return radicacion.contextoPortal();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PqrVistas.Radicada radicar(@RequestPart("datos") @Valid PqrSolicitudes.Radicar datos,
            @RequestPart(name = "adjuntos", required = false) List<MultipartFile> adjuntos) {
        return radicacion.radicarPortal(datos, PqrHttp.entradas(adjuntos));
    }

    @GetMapping("/{id}/adjuntos/{adjuntoId}")
    public ResponseEntity<byte[]> adjunto(@PathVariable Long id, @PathVariable Long adjuntoId) {
        return PqrHttp.descarga(radicacion.adjuntoPortal(id, adjuntoId));
    }
}
