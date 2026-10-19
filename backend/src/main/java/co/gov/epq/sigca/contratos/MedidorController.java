package co.gov.epq.sigca.contratos;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.contratos.ContratoDtos.MedidorInstalar;
import co.gov.epq.sigca.contratos.ContratoDtos.MedidorRetiro;
import co.gov.epq.sigca.contratos.ContratoDtos.MedidorVista;
import jakarta.validation.Valid;

/** RF-18 medidores de un predio (P-06). */
@RestController
@RequestMapping("/api")
public class MedidorController {

    private final MedidorService servicio;

    public MedidorController(MedidorService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/predios/{predioId}/medidores")
    @PreAuthorize("hasAnyAuthority('MEDIDOR_GESTIONAR','CONTRATO_CONSULTAR','LECTURA_REGISTRAR')")
    public List<MedidorVista> dePredio(@PathVariable Long predioId) {
        return servicio.dePredio(predioId);
    }

    @GetMapping("/medidores/{id}")
    @PreAuthorize("hasAnyAuthority('MEDIDOR_GESTIONAR','CONTRATO_CONSULTAR','LECTURA_REGISTRAR')")
    public MedidorVista consultar(@PathVariable Long id) {
        return servicio.consultar(id);
    }

    @PostMapping("/predios/{predioId}/medidores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MEDIDOR_GESTIONAR')")
    public MedidorVista instalar(@PathVariable Long predioId, @Valid @RequestBody MedidorInstalar req) {
        return servicio.instalar(predioId, req);
    }

    @PostMapping("/medidores/{id}/retiro")
    @PreAuthorize("hasAuthority('MEDIDOR_GESTIONAR')")
    public MedidorVista retirar(@PathVariable Long id, @Valid @RequestBody MedidorRetiro req) {
        return servicio.retirar(id, req);
    }
}
