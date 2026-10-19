package co.gov.epq.sigca.seguridad.cuentas;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.catalogo.Municipio;
import co.gov.epq.sigca.catalogo.MunicipioRepository;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.common.validation.TelefonoColombia;
import co.gov.epq.sigca.seguridad.Secretos;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuentas de funcionarios y su asignación de roles (RF-14, RN-27). La contraseña inicial es temporal: se devuelve
 * una sola vez y debe cambiarse en el primer ingreso, que además exige enrolar el segundo factor (P-20).
 */
@Service
@Transactional
public class AdminFuncionariosService {

    static final String ENTIDAD = "FUNCIONARIO";
    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_CIUDADANO = "CIUDADANO";
    public static final String ROL_GESTOR_PQR = "GESTOR_PQR";
    private static final int LONGITUD_TEMPORAL = 16;
    static final String NOMBRE = "^\\s*\\p{L}[\\p{L} .'-]{2,149}\\s*$";
    static final String MENSAJE_NOMBRE = "Nombre de 3 a 150 caracteres: letras, espacios, punto, apóstrofo o guion";
    static final String CORREO = "^\\s*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s*$";
    static final String MENSAJE_CORREO = "Ingrese un correo válido";

    private final FuncionarioRepository funcionarios;
    private final FuncionarioRolRepository funcionarioRoles;
    private final RolRepository roles;
    private final MunicipioRepository municipios;
    private final PasswordEncoder encoder;
    private final Auditoria auditoria;

    public AdminFuncionariosService(FuncionarioRepository funcionarios, FuncionarioRolRepository funcionarioRoles,
            RolRepository roles, MunicipioRepository municipios, PasswordEncoder encoder, Auditoria auditoria) {
        this.funcionarios = funcionarios;
        this.funcionarioRoles = funcionarioRoles;
        this.roles = roles;
        this.municipios = municipios;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<FuncionarioVista> buscar(String texto, Boolean activo, Pageable pageable) {
        String t = texto == null || texto.isBlank() ? null : "%" + texto.trim().toLowerCase() + "%";
        Page<Funcionario> pagina = funcionarios.buscar(t, activo, pageable);
        Map<Long, List<String>> rolesPorFuncionario = new HashMap<>();
        if (!pagina.isEmpty()) {
            for (Object[] fila : funcionarioRoles.rolesDe(pagina.map(Funcionario::getId).getContent())) {
                rolesPorFuncionario.computeIfAbsent((Long) fila[0], k -> new ArrayList<>()).add((String) fila[1]);
            }
        }
        Map<Long, String> nombres = nombresMunicipio();
        return pagina.map(f -> vista(f, rolesPorFuncionario.getOrDefault(f.getId(), List.of()), nombres));
    }

    @Transactional(readOnly = true)
    public FuncionarioVista obtener(Long id) {
        return vista(cargar(id));
    }

    public FuncionarioCreado crear(FuncionarioNuevo datos, boolean puedeAsignarRoles) {
        List<String> codigosRol = datos.roles() == null ? List.of() : datos.roles();
        if (!codigosRol.isEmpty() && !puedeAsignarRoles) {
            throw new NegocioException("ACCESO_DENEGADO", "Asignar roles requiere el permiso ROL_GESTIONAR", HttpStatus.FORBIDDEN);
        }
        List<Rol> asignar = rolesValidos(codigosRol, datos.municipioId());
        String temporal = Secretos.passwordTemporal(LONGITUD_TEMPORAL);
        Funcionario f = new Funcionario();
        f.setCodigoFuncionario(Normalizador.documento(datos.codigoFuncionario()));
        f.setNombreCompleto(Normalizador.texto(datos.nombreCompleto()));
        f.setCorreo(Normalizador.correo(datos.correo()));
        f.setTelefono(vacioANull(Normalizador.telefono(datos.telefono())));
        f.setUsername(Normalizador.correo(datos.username()));
        f.setMunicipioId(datos.municipioId());
        f.setPasswordHash(encoder.encode(temporal));
        f.setCambioPasswordRequerido(true);
        f.setDobleFactorActivo(false);
        f.setIntentosFallidos(0);
        f.setActivo(true);
        funcionarios.saveAndFlush(f);
        for (Rol r : asignar) {
            asignarRol(f.getId(), r);
        }
        auditoria.exito(OperacionAuditoria.CREAR_FUNCIONARIO, ENTIDAD, f.getId(),
                Map.of("username", f.getUsername(), "roles", asignar.stream().map(Rol::getCodigo).toList()));
        return new FuncionarioCreado(vista(f), temporal);
    }

    public FuncionarioVista actualizar(Long id, FuncionarioCambios datos) {
        Funcionario f = cargar(id);
        verificarVersion(f, datos.version());
        Map<String, Object> cambios = new LinkedHashMap<>();
        String nombre = Normalizador.texto(datos.nombreCompleto());
        String correo = Normalizador.correo(datos.correo());
        String telefono = vacioANull(Normalizador.telefono(datos.telefono()));
        if (!Objects.equals(nombre, f.getNombreCompleto())) {
            cambios.put("nombreCompleto", true);
            f.setNombreCompleto(nombre);
        }
        if (!Objects.equals(correo, f.getCorreo())) {
            cambios.put("correo", true);
            f.setCorreo(correo);
        }
        if (!Objects.equals(telefono, f.getTelefono())) {
            cambios.put("telefono", true);
            f.setTelefono(telefono);
        }
        if (!Objects.equals(datos.municipioId(), f.getMunicipioId())) {
            if (datos.municipioId() == null && funcionarios.rolesActivos(id).contains(ROL_GESTOR_PQR)) {
                throw new NegocioException("FUNCIONARIO_SIN_ROL", "Un Gestor de PQR debe tener municipio asignado (RN-20)");
            }
            cambios.put("municipioId", datos.municipioId());
            f.setMunicipioId(datos.municipioId());
        }
        if (!cambios.isEmpty()) {
            funcionarios.saveAndFlush(f);
            auditoria.exito(OperacionAuditoria.MODIFICAR_FUNCIONARIO, ENTIDAD, id, cambios);
        }
        return vista(f);
    }

    /** Deja al funcionario exactamente con los roles indicados (al menos uno), auditando cada alta y baja. */
    public FuncionarioVista asignarRoles(Long id, List<String> codigos) {
        Funcionario f = cargar(id);
        Set<String> nuevos = new LinkedHashSet<>(codigos);
        if (nuevos.isEmpty()) {
            throw new NegocioException("FUNCIONARIO_SIN_ROL", "El funcionario debe tener al menos un rol");
        }
        List<Rol> deseados = rolesValidos(List.copyOf(nuevos), f.getMunicipioId());
        Map<Long, Rol> porId = new HashMap<>();
        roles.findAll().forEach(r -> porId.put(r.getId(), r));
        Set<Long> actuales = new LinkedHashSet<>();
        funcionarioRoles.findByFuncionarioId(id).forEach(fr -> actuales.add(fr.getRolId()));
        for (Long rolId : actuales) {
            Rol r = porId.get(rolId);
            if (!nuevos.contains(r.getCodigo())) {
                if (ROL_ADMIN.equals(r.getCodigo()) && esUnoMismo(id)) {
                    throw new NegocioException("AUTOGESTION_NO_PERMITIDA", "No puede quitarse a sí mismo el rol de administrador");
                }
                funcionarioRoles.deleteById(new FuncionarioRol.Clave(id, rolId));
                auditoria.exito(OperacionAuditoria.REVOCAR_ROL, ENTIDAD, id, Map.of("rol", r.getCodigo()));
            }
        }
        for (Rol r : deseados) {
            if (!actuales.contains(r.getId())) {
                asignarRol(id, r);
            }
        }
        funcionarioRoles.flush();
        return vista(f);
    }

    public FuncionarioVista cambiarEstado(Long id, boolean activo) {
        Funcionario f = cargar(id);
        if (Boolean.valueOf(activo).equals(f.getActivo())) {
            return vista(f);
        }
        if (!activo && esUnoMismo(id)) {
            throw new NegocioException("AUTOGESTION_NO_PERMITIDA", "No puede desactivar su propia cuenta");
        }
        f.setActivo(activo);
        f.setFechaDesactivacion(activo ? null : LocalDateTime.now());
        funcionarios.saveAndFlush(f);
        if (activo) {
            auditoria.exito(OperacionAuditoria.MODIFICAR_FUNCIONARIO, ENTIDAD, id, Map.of("accion", "ACTIVAR"));
        } else {
            auditoria.exito(OperacionAuditoria.DESACTIVAR_FUNCIONARIO, ENTIDAD, id, null);
        }
        return vista(f);
    }

    /** Para un funcionario que perdió su app autenticadora: deberá enrolarla de nuevo en el siguiente ingreso. */
    public FuncionarioVista reiniciarDobleFactor(Long id) {
        Funcionario f = cargar(id);
        f.setDobleFactorActivo(false);
        f.setSecretoDobleFactor(null);
        // CK_FUNC_2FA_OBLIGATORIO: sin segundo factor no puede quedar un acceso registrado
        f.setUltimoAcceso(null);
        funcionarios.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.MODIFICAR_FUNCIONARIO, ENTIDAD, id, Map.of("accion", "REINICIAR_2FA"));
        return vista(f);
    }

    public FuncionarioVista desbloquear(Long id) {
        Funcionario f = cargar(id);
        f.setIntentosFallidos(0);
        f.setBloqueadoHasta(null);
        funcionarios.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.MODIFICAR_FUNCIONARIO, ENTIDAD, id, Map.of("accion", "DESBLOQUEAR"));
        return vista(f);
    }

    public FuncionarioCreado restablecerPassword(Long id) {
        Funcionario f = cargar(id);
        String temporal = Secretos.passwordTemporal(LONGITUD_TEMPORAL);
        f.setPasswordHash(encoder.encode(temporal));
        f.setCambioPasswordRequerido(true);
        f.setIntentosFallidos(0);
        f.setBloqueadoHasta(null);
        funcionarios.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.MODIFICAR_FUNCIONARIO, ENTIDAD, id, Map.of("accion", "RESTABLECER_PASSWORD"));
        return new FuncionarioCreado(vista(f), temporal);
    }

    private void asignarRol(Long funcionarioId, Rol r) {
        FuncionarioRol fr = new FuncionarioRol();
        fr.setFuncionarioId(funcionarioId);
        fr.setRolId(r.getId());
        funcionarioRoles.saveAndFlush(fr);
        auditoria.exito(OperacionAuditoria.ASIGNAR_ROL, ENTIDAD, funcionarioId, Map.of("rol", r.getCodigo()));
    }

    /** Reglas que también aplica TRG_FUNCIONARIO_ROL_BI, con un mensaje claro antes de llegar a la BD. */
    private List<Rol> rolesValidos(List<String> codigos, Long municipioId) {
        List<Rol> lista = new ArrayList<>();
        for (String codigo : new LinkedHashSet<>(codigos)) {
            Rol r = roles.findByCodigo(codigo).orElseThrow(() -> new RecursoNoEncontradoException("Rol", codigo));
            if (ROL_CIUDADANO.equals(r.getCodigo())) {
                throw new NegocioException("FUNCIONARIO_SIN_ROL", "El rol CIUDADANO es exclusivo de las cuentas del portal");
            }
            if (!Boolean.TRUE.equals(r.getActivo())) {
                throw new NegocioException("ROL_INACTIVO", "El rol " + codigo + " está inactivo");
            }
            if (ROL_GESTOR_PQR.equals(r.getCodigo()) && municipioId == null) {
                throw new NegocioException("FUNCIONARIO_SIN_ROL",
                        "Para asignar el rol Gestor de PQR el funcionario debe tener municipio (RN-20)");
            }
            lista.add(r);
        }
        return lista;
    }

    private static boolean esUnoMismo(Long funcionarioId) {
        return UsuarioActual.opcional().map(u -> funcionarioId.equals(u.funcionarioId())).orElse(false);
    }

    private static void verificarVersion(Funcionario f, Long version) {
        if (version != null && !version.equals(f.getVersion())) {
            throw new ConflictoException("MODIFICACION_CONCURRENTE",
                    "El funcionario fue modificado por otro usuario. Recargue e intente de nuevo");
        }
    }

    private Funcionario cargar(Long id) {
        return funcionarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Funcionario", id));
    }

    private FuncionarioVista vista(Funcionario f) {
        List<String> codigos = funcionarioRoles.rolesDe(List.of(f.getId())).stream().map(o -> (String) o[1]).toList();
        return vista(f, codigos, nombresMunicipio());
    }

    private Map<Long, String> nombresMunicipio() {
        Map<Long, String> m = new HashMap<>();
        for (Municipio mu : municipios.findAll()) {
            m.put(mu.getId(), mu.getNombre());
        }
        return m;
    }

    private static FuncionarioVista vista(Funcionario f, List<String> roles, Map<Long, String> municipios) {
        return new FuncionarioVista(f.getId(), f.getCodigoFuncionario(), f.getNombreCompleto(), f.getCorreo(), f.getTelefono(),
                f.getUsername(), f.getMunicipioId(), f.getMunicipioId() == null ? null : municipios.get(f.getMunicipioId()),
                roles, Boolean.TRUE.equals(f.getActivo()), Boolean.TRUE.equals(f.getDobleFactorActivo()),
                Boolean.TRUE.equals(f.getCambioPasswordRequerido()),
                f.getBloqueadoHasta() != null && f.getBloqueadoHasta().isAfter(LocalDateTime.now()) ? f.getBloqueadoHasta() : null,
                f.getUltimoAcceso(), f.getFechaDesactivacion(), f.getVersion());
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public record FuncionarioVista(Long id, String codigoFuncionario, String nombreCompleto, String correo, String telefono,
            String username, Long municipioId, String municipio, List<String> roles, boolean activo,
            boolean dobleFactorActivo, boolean cambioPasswordRequerido, LocalDateTime bloqueadoHasta,
            LocalDateTime ultimoAcceso, LocalDateTime fechaDesactivacion, Long version) {
    }

    /** Mismas reglas de CK_FUNC_*: código en mayúsculas, usuario en minúsculas, nombre solo con letras. */
    public record FuncionarioNuevo(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "Código de 3 a 20 caracteres: mayúsculas, dígitos o guion") String codigoFuncionario,
            @NotBlank @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE) String nombreCompleto,
            @NotBlank @Size(max = 254) @Pattern(regexp = CORREO, message = MENSAJE_CORREO) String correo,
            @TelefonoColombia String telefono,
            @NotBlank @Pattern(regexp = "^[a-z0-9._-]{3,60}$", message = "Usuario de 3 a 60 caracteres: minúsculas, dígitos, punto, guion o guion bajo") String username,
            Long municipioId,
            @Size(max = 10) List<@NotBlank String> roles) {
    }

    public record FuncionarioCambios(
            @NotBlank @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE) String nombreCompleto,
            @NotBlank @Size(max = 254) @Pattern(regexp = CORREO, message = MENSAJE_CORREO) String correo,
            @TelefonoColombia String telefono,
            Long municipioId,
            @NotNull Long version) {
    }

    /** La contraseña temporal solo viaja en esta respuesta; no se guarda en claro ni se envía por correo. */
    public record FuncionarioCreado(FuncionarioVista funcionario, String passwordTemporal) {
    }
}
