package co.gov.epq.sigca.pqr;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

/** Radicación y consulta de PQR sin sesión (P-14, RF-10). La ruta /api/publico/** es pública en SecurityConfig. */
@RestController
@RequestMapping("/api/publico/pqr")
public class PqrPublicoController {

    private final RadicacionPqrService radicacion;

    public PqrPublicoController(RadicacionPqrService radicacion) {
        this.radicacion = radicacion;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("permitAll()")
    public PqrVistas.Radicada radicar(@RequestPart("datos") @Valid PqrSolicitudes.Radicar datos,
            @RequestPart(name = "adjuntos", required = false) List<MultipartFile> adjuntos) {
        return radicacion.radicarPublica(datos, PqrHttp.entradas(adjuntos));
    }

    @PostMapping("/consulta")
    @PreAuthorize("permitAll()")
    public PqrVistas.ConsultaPublica consultar(@RequestBody @Valid PqrSolicitudes.ConsultaPublica consulta) {
        return radicacion.consultar(consulta);
    }
}
