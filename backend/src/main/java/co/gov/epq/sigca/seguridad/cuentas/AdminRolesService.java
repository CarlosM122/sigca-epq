package co.gov.epq.sigca.seguridad.cuentas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;

/**
 * Matriz rol-permiso (P-16, RN-27). Los cambios aplican a cada sesión cuando renueva su token (máximo 15 min).
 */
@Service
@Transactional
public class AdminRolesService {

    static final String MODULO_PORTAL = "PORTAL";
    static final String PERMISO_ROLES = "ROL_GESTIONAR";

    private final RolRepository roles;
    private final PermisoRepository permisos;
    private final Auditoria auditoria;

    public AdminRolesService(RolRepository roles, PermisoRepository permisos, Auditoria auditoria) {
        this.roles = roles;
        this.permisos = permisos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<RolVista> roles() {
        Map<String, List<String>> matriz = new HashMap<>();
        for (Object[] fila : roles.matriz()) {
            matriz.computeIfAbsent((String) fila[0], k -> new ArrayList<>()).add((String) fila[1]);
        }
        return roles.findAllByOrderByNombre().stream()
                .map(r -> new RolVista(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(),
                        Boolean.TRUE.equals(r.getActivo()), matriz.getOrDefault(r.getCodigo(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermisoVista> permisos() {
        return permisos.findAllByOrderByModuloAscCodigoAsc().stream()
                .map(p -> new PermisoVista(p.getId(), p.getCodigo(), p.getModulo(), p.getDescripcion()))
                .toList();
    }

    /**
     * Deja al rol con exactamente esos permisos. El rol CIUDADANO solo admite permisos del portal y los demás roles
     * no los admiten (los datos del portal se filtran por el ciudadano autenticado). ADMIN conserva ROL_GESTIONAR
     * para que la matriz no quede sin quien la administre.
     */
    public RolVista asignarPermisos(String codigoRol, List<String> codigos) {
        Rol rol = roles.findByCodigo(codigoRol).orElseThrow(() -> new RecursoNoEncontradoException("Rol", codigoRol));
        Map<String, Permiso> porCodigo = new LinkedHashMap<>();
        permisos.findAll().forEach(p -> porCodigo.put(p.getCodigo(), p));
        Set<String> deseados = new TreeSet<>(codigos);
        boolean ciudadano = AdminFuncionariosService.ROL_CIUDADANO.equals(rol.getCodigo());
        for (String c : deseados) {
            Permiso p = porCodigo.get(c);
            if (p == null) {
                throw new RecursoNoEncontradoException("Permiso", c);
            }
            if (ciudadano != MODULO_PORTAL.equals(p.getModulo())) {
                throw new NegocioException("PERMISO_NO_APLICA", ciudadano
                        ? "El rol CIUDADANO solo admite permisos del portal"
                        : "Los permisos del portal son exclusivos del rol CIUDADANO");
            }
        }
        if (AdminFuncionariosService.ROL_ADMIN.equals(rol.getCodigo()) && !deseados.contains(PERMISO_ROLES)) {
            throw new NegocioException("PERMISO_NO_APLICA", "El rol ADMIN debe conservar el permiso ROL_GESTIONAR");
        }
        Set<String> actuales = new LinkedHashSet<>(roles.permisosDe(rol.getId()));
        List<String> agregados = new ArrayList<>();
        List<String> quitados = new ArrayList<>();
        for (String c : actuales) {
            if (!deseados.contains(c)) {
                roles.quitar(rol.getId(), porCodigo.get(c).getId());
                quitados.add(c);
            }
        }
        for (String c : deseados) {
            if (!actuales.contains(c)) {
                roles.otorgar(rol.getId(), porCodigo.get(c).getId());
                agregados.add(c);
            }
        }
        if (!agregados.isEmpty() || !quitados.isEmpty()) {
            auditoria.exito(OperacionAuditoria.MODIFICAR_PERMISOS, "ROL", rol.getId(),
                    Map.of("rol", rol.getCodigo(), "agregados", agregados, "quitados", quitados));
        }
        return new RolVista(rol.getId(), rol.getCodigo(), rol.getNombre(), rol.getDescripcion(),
                Boolean.TRUE.equals(rol.getActivo()), List.copyOf(deseados));
    }

    public record RolVista(Long id, String codigo, String nombre, String descripcion, boolean activo, List<String> permisos) {
    }

    public record PermisoVista(Long id, String codigo, String modulo, String descripcion) {
    }
}
