package co.gov.epq.sigca.seguridad;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.seguridad.cuentas.AdminFuncionariosService;
import co.gov.epq.sigca.seguridad.cuentas.AdminFuncionariosService.FuncionarioCambios;
import co.gov.epq.sigca.seguridad.cuentas.AdminFuncionariosService.FuncionarioCreado;
import co.gov.epq.sigca.seguridad.cuentas.AdminFuncionariosService.FuncionarioNuevo;
import co.gov.epq.sigca.seguridad.cuentas.AdminFuncionariosService.FuncionarioVista;
import co.gov.epq.sigca.seguridad.cuentas.AdminRolesService;
import co.gov.epq.sigca.seguridad.cuentas.AdminRolesService.PermisoVista;
import co.gov.epq.sigca.seguridad.cuentas.AdminRolesService.RolVista;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Administración de funcionarios, roles y permisos (RF-14, RN-27: solo el Administrador).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSeguridadController {

    private final AdminFuncionariosService funcionarios;
    private final AdminRolesService roles;

    public AdminSeguridadController(AdminFuncionariosService funcionarios, AdminRolesService roles) {
        this.funcionarios = funcionarios;
        this.roles = roles;
    }

    @GetMapping("/funcionarios")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public Page<FuncionarioVista> buscar(@RequestParam(required = false) @Size(max = 60) String texto,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return funcionarios.buscar(texto, activo, PageRequest.of(page, size));
    }

    @GetMapping("/funcionarios/{id}")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public FuncionarioVista obtener(@PathVariable Long id) {
        return funcionarios.obtener(id);
    }

    /** Devuelve la contraseña temporal una sola vez. Asignar roles al crear exige además ROL_GESTIONAR. */
    @PostMapping("/funcionarios")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public FuncionarioCreado crear(@Valid @RequestBody FuncionarioNuevo datos) {
        return funcionarios.crear(datos, UsuarioActual.funcionario().tienePermiso("ROL_GESTIONAR"));
    }

    @PutMapping("/funcionarios/{id}")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public FuncionarioVista actualizar(@PathVariable Long id, @Valid @RequestBody FuncionarioCambios datos) {
        return funcionarios.actualizar(id, datos);
    }

    @PutMapping("/funcionarios/{id}/roles")
    @PreAuthorize("hasAuthority('ROL_GESTIONAR')")
    public FuncionarioVista asignarRoles(@PathVariable Long id, @Valid @RequestBody Roles datos) {
        return funcionarios.asignarRoles(id, datos.roles());
    }

    @PostMapping("/funcionarios/{id}/estado")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public FuncionarioVista cambiarEstado(@PathVariable Long id, @Valid @RequestBody Estado datos) {
        return funcionarios.cambiarEstado(id, datos.activo());
    }

    @PostMapping("/funcionarios/{id}/desbloqueo")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public FuncionarioVista desbloquear(@PathVariable Long id) {
        return funcionarios.desbloquear(id);
    }

    @PostMapping("/funcionarios/{id}/doble-factor/reinicio")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public FuncionarioVista reiniciarDobleFactor(@PathVariable Long id) {
        return funcionarios.reiniciarDobleFactor(id);
    }

    @PostMapping("/funcionarios/{id}/password-temporal")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GESTIONAR')")
    public FuncionarioCreado restablecerPassword(@PathVariable Long id) {
        return funcionarios.restablecerPassword(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('FUNCIONARIO_GESTIONAR', 'ROL_GESTIONAR')")
    public List<RolVista> roles() {
        return roles.roles();
    }

    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('ROL_GESTIONAR')")
    public List<PermisoVista> permisos() {
        return roles.permisos();
    }

    @PutMapping("/roles/{codigo}/permisos")
    @PreAuthorize("hasAuthority('ROL_GESTIONAR')")
    public RolVista asignarPermisos(@PathVariable String codigo, @Valid @RequestBody Permisos datos) {
        return roles.asignarPermisos(codigo, datos.permisos());
    }

    public record Roles(@NotNull @Size(min = 1, max = 10, message = "Asigne al menos un rol") List<@NotBlank String> roles) {
    }

    public record Permisos(@NotNull @Size(max = 200) List<@NotBlank String> permisos) {
    }

    public record Estado(@NotNull Boolean activo) {
    }
}
