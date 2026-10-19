package co.gov.epq.sigca.cartera;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cartera vencida y cobro persuasivo (RF-23, P-11). */
@RestController
@RequestMapping("/api/cartera")
@Validated
public class CarteraController {

    private final MorososService morosos;
    private final CobroService cobros;

    public CarteraController(MorososService morosos, CobroService cobros) {
        this.morosos = morosos;
        this.cobros = cobros;
    }

    @GetMapping("/morosos")
    @PreAuthorize("hasAuthority('CARTERA_CONSULTAR')")
    public Page<CarteraVistas.Moroso> morosos(@RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) @Pattern(regexp = "30|60|90|MAS_90", message = "Antigüedad inválida") String antiguedad,
            @RequestParam(required = false) @Pattern(regexp = "SIN_COBRO|ACTIVO|CANDIDATO|AVISADO|EN_ACUERDO", message = "Estado de cobro inválido") String cobro,
            @RequestParam(required = false) @Size(max = 60) String texto,
            @PageableDefault(size = 20) Pageable pageable) {
        return morosos.listar(new CarteraConsultas.FiltroMorosos(municipioId, antiguedad, cobro, texto), pageable);
    }

    @GetMapping(value = "/morosos.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORTE_CARTERA')")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) @Pattern(regexp = "30|60|90|MAS_90", message = "Antigüedad inválida") String antiguedad,
            @RequestParam(required = false) @Pattern(regexp = "SIN_COBRO|ACTIVO|CANDIDATO|AVISADO|EN_ACUERDO", message = "Estado de cobro inválido") String cobro,
            @RequestParam(required = false) @Size(max = 60) String texto) {
        byte[] csv = morosos.exportar(new CarteraConsultas.FiltroMorosos(municipioId, antiguedad, cobro, texto));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("morosos-" + LocalDate.now() + ".csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }

    @GetMapping("/cobros/{id}")
    @PreAuthorize("hasAuthority('CARTERA_CONSULTAR')")
    public CarteraVistas.Cobro cobro(@PathVariable Long id) {
        return cobros.consultar(id);
    }

    @PostMapping("/cobros")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COBRO_GESTIONAR')")
    public CarteraVistas.Cobro iniciar(@RequestBody @Valid CarteraSolicitudes.IniciarCobro s) {
        return cobros.iniciar(s);
    }

    @PostMapping("/cobros/{id}/candidato")
    @PreAuthorize("hasAuthority('COBRO_GESTIONAR')")
    public CarteraVistas.Cobro candidato(@PathVariable Long id, @RequestBody(required = false) @Valid CarteraSolicitudes.Observacion s) {
        return cobros.marcarCandidato(id, s == null ? new CarteraSolicitudes.Observacion(null) : s);
    }

    @PostMapping("/cobros/{id}/aviso")
    @PreAuthorize("hasAuthority('COBRO_GESTIONAR')")
    public CarteraVistas.Cobro aviso(@PathVariable Long id, @RequestBody(required = false) @Valid CarteraSolicitudes.Observacion s) {
        return cobros.avisar(id, s == null ? new CarteraSolicitudes.Observacion(null) : s);
    }

    @PostMapping("/cobros/{id}/orden-suspension")
    @PreAuthorize("hasAuthority('COBRO_GESTIONAR')")
    public CarteraVistas.Cobro ordenSuspension(@PathVariable Long id,
            @RequestBody(required = false) @Valid CarteraSolicitudes.Observacion s) {
        return cobros.ordenarSuspension(id, s == null ? new CarteraSolicitudes.Observacion(null) : s);
    }
}
