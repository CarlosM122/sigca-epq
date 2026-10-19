package co.gov.epq.sigca.usuarios;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.usuarios.UsuarioDtos.ContactoActualizar;
import co.gov.epq.sigca.usuarios.UsuarioDtos.Inactivacion;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioCrear;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioDetalle;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioModificar;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioVista;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** CU-01 Gestionar usuario (RF-01, RF-02, RF-03, RF-16). */
@RestController
@RequestMapping("/api/usuarios")
@Validated
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USUARIO_CREAR')")
    public UsuarioVista crear(@Valid @RequestBody UsuarioCrear req) {
        return servicio.crear(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USUARIO_CONSULTAR')")
    public Page<UsuarioVista> buscar(@RequestParam(required = false) @Size(max = 150) String texto,
            @RequestParam(required = false) TipoIdentificacion tipoIdentificacion,
            @RequestParam(required = false) @Size(max = 15) String numeroIdentificacion,
            @RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20) Pageable pageable) {
        return servicio.buscar(texto, tipoIdentificacion, numeroIdentificacion, municipioId, activo, Paginas.limitar(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIO_CONSULTAR')")
    public UsuarioDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIO_MODIFICAR')")
    public UsuarioVista modificar(@PathVariable Long id, @Valid @RequestBody UsuarioModificar req) {
        return servicio.modificar(id, req);
    }

    @PutMapping("/{id}/contacto")
    @PreAuthorize("hasAuthority('USUARIO_MODIFICAR')")
    public UsuarioVista actualizarContacto(@PathVariable Long id, @Valid @RequestBody ContactoActualizar req) {
        return servicio.actualizarContacto(id, req);
    }

    @PostMapping("/{id}/inactivacion")
    @PreAuthorize("hasAuthority('USUARIO_INACTIVAR')")
    public UsuarioVista inactivar(@PathVariable Long id, @Valid @RequestBody Inactivacion req) {
        return servicio.inactivar(id, req);
    }

    @PostMapping("/{id}/verificacion-rnec")
    @PreAuthorize("hasAuthority('USUARIO_MODIFICAR')")
    public UsuarioVista reverificar(@PathVariable Long id) {
        return servicio.reverificar(id);
    }
}
