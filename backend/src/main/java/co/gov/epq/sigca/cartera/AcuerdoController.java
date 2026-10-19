package co.gov.epq.sigca.cartera;

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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Acuerdos de pago (RF-23, P-12). */
@RestController
@RequestMapping("/api/cartera")
@Validated
public class AcuerdoController {

    private final AcuerdoService acuerdos;

    public AcuerdoController(AcuerdoService acuerdos) {
        this.acuerdos = acuerdos;
    }

    @GetMapping("/usuarios/{usuarioId}/facturas-vencidas")
    @PreAuthorize("hasAuthority('ACUERDO_GESTIONAR')")
    public CarteraVistas.DeudaUsuario deuda(@PathVariable Long usuarioId) {
        return acuerdos.deuda(usuarioId);
    }

    @PostMapping("/acuerdos/simulacion")
    @PreAuthorize("hasAuthority('ACUERDO_GESTIONAR')")
    public CarteraVistas.Simulacion simular(@RequestBody @Valid CarteraSolicitudes.AcuerdoDatos d) {
        return acuerdos.simular(d);
    }

    @PostMapping("/acuerdos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ACUERDO_GESTIONAR')")
    public CarteraVistas.AcuerdoDetalle crear(@RequestBody @Valid CarteraSolicitudes.AcuerdoDatos d) {
        return acuerdos.crear(d);
    }

    @GetMapping("/acuerdos")
    @PreAuthorize("hasAnyAuthority('CARTERA_CONSULTAR','ACUERDO_GESTIONAR')")
    public Page<CarteraVistas.Acuerdo> listar(@RequestParam(required = false) EstadoAcuerdo estado,
            @RequestParam(required = false) Long usuarioId, @RequestParam(required = false) @Size(max = 20) String texto,
            @PageableDefault(size = 20) Pageable pageable) {
        return acuerdos.listar(estado, usuarioId, texto, pageable);
    }

    @GetMapping("/acuerdos/{id}")
    @PreAuthorize("hasAnyAuthority('CARTERA_CONSULTAR','ACUERDO_GESTIONAR')")
    public CarteraVistas.AcuerdoDetalle detalle(@PathVariable Long id) {
        return acuerdos.consultar(id);
    }

    @PostMapping("/acuerdos/{id}/anulacion")
    @PreAuthorize("hasAuthority('ACUERDO_GESTIONAR')")
    public CarteraVistas.AcuerdoDetalle anular(@PathVariable Long id, @RequestBody @Valid CarteraSolicitudes.Anulacion s) {
        return acuerdos.anular(id, s);
    }
}
