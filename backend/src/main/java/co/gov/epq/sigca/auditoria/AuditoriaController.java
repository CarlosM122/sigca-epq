package co.gov.epq.sigca.auditoria;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.auditoria.ConsultaAuditoriaService.RegistroAuditoria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Consulta del log de auditoría por el Administrador (RF-15). */
@RestController
@RequestMapping("/api/admin/auditoria")
@PreAuthorize("hasAuthority('AUDITORIA_CONSULTAR')")
public class AuditoriaController {

    private final ConsultaAuditoriaService servicio;

    public AuditoriaController(ConsultaAuditoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Page<RegistroAuditoria> buscar(
            @RequestParam(required = false) OperacionAuditoria operacion,
            @RequestParam(required = false) @Size(max = 30) String entidad,
            @RequestParam(required = false) Long entidadId,
            @RequestParam(required = false) @Size(max = 60) String usuario,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) ResultadoAuditoria resultado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return servicio.buscar(operacion, entidad, entidadId, usuario, usuarioId, resultado, desde, hasta,
                PageRequest.of(page, size));
    }

    /** Operaciones auditables, para el filtro de la pantalla. */
    @GetMapping("/operaciones")
    public List<OperacionAuditoria> operaciones() {
        return Arrays.asList(OperacionAuditoria.values());
    }
}
