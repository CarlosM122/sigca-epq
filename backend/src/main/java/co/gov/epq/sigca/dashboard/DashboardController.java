package co.gov.epq.sigca.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Tablero gerencial (RF-24, P-28). La respuesta trae refrescoSegundos (DASHBOARD_REFRESCO_SEG) para el cliente. */
@RestController
@RequestMapping("/api/dashboard")
@Validated
public class DashboardController {

    private final DashboardService servicio;

    public DashboardController(DashboardService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_VER')")
    public DashboardVistas.Tablero tablero(
            @RequestParam(required = false) @Min(value = 200001, message = "Periodo inválido") @Max(value = 209912, message = "Periodo inválido") Integer periodo,
            @RequestParam(required = false) Long municipioId) {
        if (periodo != null && (periodo % 100 < 1 || periodo % 100 > 12)) {
            throw new co.gov.epq.sigca.common.error.NegocioException("PARAMETRO_INVALIDO", "Periodo inválido");
        }
        return servicio.tablero(periodo, municipioId);
    }
}
