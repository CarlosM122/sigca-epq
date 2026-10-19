package co.gov.epq.sigca.usuarios;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.validation.DocumentoIdentidad;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.contratos.ContratoService;
import co.gov.epq.sigca.usuarios.UsuarioDtos.ContactoActualizar;
import co.gov.epq.sigca.usuarios.UsuarioDtos.Inactivacion;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioCrear;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioDetalle;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioModificar;
import co.gov.epq.sigca.usuarios.UsuarioDtos.UsuarioVista;

/**
 * CU-01 Gestionar usuario: registro con verificación RNEC (RF-01), consulta (RF-03), actualización (RF-02)
 * e inactivación lógica (RF-16). La identificación se guarda cifrada, con HMAC para buscar y con máscara para mostrar.
 */
@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    static final String ENTIDAD = "USUARIO";

    private final UsuarioRepository usuarios;
    private final PredioService predios;
    private final ContratoService contratos;
    private final CifradorIdentificacion cifrador;
    private final ValidadorRnec rnec;
    private final CatalogoTerritorial catalogo;
    private final Auditoria auditoria;

    public UsuarioService(UsuarioRepository usuarios, PredioService predios, ContratoService contratos,
            CifradorIdentificacion cifrador, ValidadorRnec rnec, CatalogoTerritorial catalogo, Auditoria auditoria) {
        this.usuarios = usuarios;
        this.predios = predios;
        this.contratos = contratos;
        this.cifrador = cifrador;
        this.rnec = rnec;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
    }

    /** CO-03: unicidad (4a), RNEC con degradación a PENDIENTE (4b), código USR- generado por la BD y auditoría. */
    @Transactional
    public UsuarioVista crear(UsuarioCrear req) {
        String numero = Normalizador.documento(req.numeroIdentificacion());
        String hash = cifrador.hash(req.tipoIdentificacion(), numero);
        usuarios.findByTipoIdentificacionAndNumeroIdentificacionHash(req.tipoIdentificacion(), hash).ifPresent(u -> {
            throw new ConflictoException("DUPLICADO",
                    "Ya existe un usuario con ese tipo y número de identificación (" + u.getCodigo() + ")");
        });
        catalogo.exigirMunicipioActivo(req.municipioId());
        String nombre = Normalizador.texto(req.nombreCompleto());

        EstadoVerificacionRnec verificacion;
        String advertencia = null;
        try {
            if (rnec.verificar(req.tipoIdentificacion(), numero, nombre) == ValidadorRnec.Resultado.NO_COINCIDE) {
                auditoria.fallo(OperacionAuditoria.CREAR_USUARIO, ENTIDAD, null,
                        Map.of("motivo", "RNEC_NO_COINCIDE", "tipoIdentificacion", req.tipoIdentificacion()));
                throw new NegocioException("RNEC_NO_COINCIDE",
                        "La identificación no coincide con los registros de la Registraduría. Verifique los datos del documento");
            }
            verificacion = EstadoVerificacionRnec.VERIFICADO;
        } catch (ValidadorRnec.RnecNoDisponibleException e) {
            log.warn("RNEC no disponible al registrar un usuario: {}", e.getMessage());
            verificacion = EstadoVerificacionRnec.PENDIENTE;
            advertencia = "La Registraduría no respondió: el usuario quedó pendiente de validación y se verificará automáticamente";
        }

        LocalDateTime ahora = LocalDateTime.now();
        Usuario u = new Usuario();
        u.setTipoPersona(req.tipoPersona());
        u.setTipoIdentificacion(req.tipoIdentificacion());
        u.setNumeroIdentificacionCifrado(cifrador.cifrar(numero));
        u.setNumeroIdentificacionHash(hash);
        u.setNumeroIdentificacionMascara(CifradorIdentificacion.mascara(numero));
        u.setNombreCompleto(nombre);
        u.setCorreo(Normalizador.correo(req.correo()));
        u.setTelefono(Normalizador.telefono(req.telefono()));
        u.setDireccionNotificacion(Normalizador.texto(req.direccionNotificacion()));
        u.setMunicipioId(req.municipioId());
        u.setAutorizaTratamientoDatos(true);
        u.setFechaAutorizacionDatos(ahora);
        u.setEstadoVerificacionRnec(verificacion);
        u.setFechaVerificacionRnec(verificacion == EstadoVerificacionRnec.PENDIENTE ? null : ahora);
        u.setActivo(true);
        usuarios.saveAndFlush(u);
        auditoria.exito(OperacionAuditoria.CREAR_USUARIO, ENTIDAD, u.getId(), Map.of("codigo", u.getCodigo(),
                "tipoIdentificacion", u.getTipoIdentificacion(), "verificacionRnec", verificacion,
                "municipioId", u.getMunicipioId()));
        return vista(u, advertencia);
    }

    /**
     * RF-03: por nombre o código (texto), documento exacto (se busca por HMAC; sin tipo se prueban todos los tipos),
     * municipio y estado.
     */
    @Transactional(readOnly = true)
    public Page<UsuarioVista> buscar(String texto, TipoIdentificacion tipo, String numero, Long municipioId, Boolean activo,
            Pageable pageable) {
        List<Specification<Usuario>> filtros = new ArrayList<>();
        if (!Textos.vacio(texto)) {
            String patron = "%" + texto.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ") + "%";
            filtros.add((r, q, cb) -> cb.or(cb.like(cb.upper(r.get("nombreCompleto")), patron), cb.like(r.get("codigo"), patron)));
        }
        if (!Textos.vacio(numero)) {
            String n = Normalizador.documento(numero);
            List<TipoIdentificacion> tipos = tipo != null ? List.of(tipo)
                    : Arrays.stream(TipoIdentificacion.values()).filter(t -> DocumentoIdentidad.esValido(t, n)).toList();
            if (tipos.isEmpty()) {
                return Page.empty(pageable);
            }
            List<String> hashes = tipos.stream().map(t -> cifrador.hash(t, n)).toList();
            filtros.add((r, q, cb) -> r.get("numeroIdentificacionHash").in(hashes));
        } else if (tipo != null) {
            filtros.add((r, q, cb) -> cb.equal(r.get("tipoIdentificacion"), tipo));
        }
        if (municipioId != null) {
            filtros.add((r, q, cb) -> cb.equal(r.get("municipioId"), municipioId));
        }
        if (activo != null) {
            filtros.add((r, q, cb) -> cb.equal(r.get("activo"), activo));
        }
        Pageable orden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("nombreCompleto", "id"));
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        return usuarios.findAll(Specification.allOf(filtros), orden).map(u -> vista(u, municipios, null, null));
    }

    @Transactional(readOnly = true)
    public UsuarioDetalle detalle(Long id) {
        Usuario u = obtener(id);
        return new UsuarioDetalle(vista(u, null), predios.delUsuario(id), contratos.deUsuario(id));
    }

    @Transactional
    public UsuarioVista modificar(Long id, UsuarioModificar req) {
        Usuario u = obtenerActivo(id);
        Versiones.exigir(req.version(), u.getVersion());
        String nombre = Normalizador.texto(req.nombreCompleto());
        String error = NombreSegunPersona.Validador.errorNombre(u.getTipoPersona(), nombre);
        if (error != null) {
            throw new NegocioException("VALIDACION", error, HttpStatus.BAD_REQUEST);
        }
        if (!Objects.equals(u.getMunicipioId(), req.municipioId())) {
            catalogo.exigirMunicipioActivo(req.municipioId());
        }
        Map<String, Object> cambios = new LinkedHashMap<>();
        if (!Objects.equals(u.getNombreCompleto(), nombre)) {
            cambios.put("nombreCompleto", true);
            u.setNombreCompleto(nombre);
        }
        if (!Objects.equals(u.getMunicipioId(), req.municipioId())) {
            cambios.put("municipioId", req.municipioId());
            u.setMunicipioId(req.municipioId());
        }
        aplicarContacto(u, req.correo(), req.telefono(), req.direccionNotificacion(), cambios);
        if (!cambios.isEmpty()) {
            usuarios.saveAndFlush(u);
            auditoria.exito(OperacionAuditoria.MODIFICAR_USUARIO, ENTIDAD, id, Map.of("camposModificados", cambios.keySet()));
        }
        return vista(u, null);
    }

    @Transactional
    public UsuarioVista actualizarContacto(Long id, ContactoActualizar req) {
        Usuario u = obtenerActivo(id);
        Versiones.exigir(req.version(), u.getVersion());
        Map<String, Object> cambios = new LinkedHashMap<>();
        aplicarContacto(u, req.correo(), req.telefono(), req.direccionNotificacion(), cambios);
        if (!cambios.isEmpty()) {
            usuarios.saveAndFlush(u);
            auditoria.exito(OperacionAuditoria.ACTUALIZAR_CONTACTO, ENTIDAD, id, Map.of("camposModificados", cambios.keySet()));
        }
        return vista(u, null);
    }

    /** RF-16 / P-24: la BD rechaza (-20012) si el usuario tiene contratos vigentes o saldo pendiente. */
    @Transactional
    public UsuarioVista inactivar(Long id, Inactivacion req) {
        Usuario u = obtenerActivo(id);
        Versiones.exigir(req.version(), u.getVersion());
        u.setActivo(false);
        u.setFechaInactivacion(LocalDateTime.now());
        u.setMotivoInactivacion(Normalizador.texto(req.motivo()));
        usuarios.saveAndFlush(u);
        auditoria.exito(OperacionAuditoria.INACTIVAR_USUARIO, ENTIDAD, id, Map.of("codigo", u.getCodigo()));
        return vista(u, null);
    }

    /** Reintenta la verificación con la RNEC de un usuario PENDIENTE (RN-11). */
    @Transactional
    public UsuarioVista reverificar(Long id) {
        Usuario u = obtener(id);
        if (u.getEstadoVerificacionRnec() != EstadoVerificacionRnec.PENDIENTE) {
            throw new NegocioException("RNEC_YA_VERIFICADO", "La identificación ya fue verificada: " + u.getEstadoVerificacionRnec());
        }
        try {
            verificarPendiente(u);
        } catch (ValidadorRnec.RnecNoDisponibleException e) {
            throw new NegocioException("RNEC_NO_DISPONIBLE", "La Registraduría no responde. Intente más tarde",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        return vista(u, null);
    }

    /** Usado por el proceso programado; propaga RnecNoDisponibleException para que el lote se detenga. */
    @Transactional
    public void verificarPendiente(Usuario u) {
        String numero = cifrador.descifrar(u.getNumeroIdentificacionCifrado());
        ValidadorRnec.Resultado r = rnec.verificar(u.getTipoIdentificacion(), numero, u.getNombreCompleto());
        u.setEstadoVerificacionRnec(r == ValidadorRnec.Resultado.COINCIDE ? EstadoVerificacionRnec.VERIFICADO
                : EstadoVerificacionRnec.RECHAZADO);
        u.setFechaVerificacionRnec(LocalDateTime.now());
        usuarios.saveAndFlush(u);
        auditoria.exito(OperacionAuditoria.VERIFICAR_RNEC, ENTIDAD, u.getId(), Map.of("resultado", u.getEstadoVerificacionRnec()));
    }

    Usuario obtener(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private Usuario obtenerActivo(Long id) {
        Usuario u = obtener(id);
        if (!Boolean.TRUE.equals(u.getActivo())) {
            throw new NegocioException("USUARIO_INACTIVO", "El usuario está inactivo y no se modifica");
        }
        return u;
    }

    private static void aplicarContacto(Usuario u, String correo, String telefono, String direccion, Map<String, Object> cambios) {
        String c = Normalizador.correo(correo);
        String t = Normalizador.telefono(telefono);
        String d = Normalizador.texto(direccion);
        if (!Objects.equals(u.getCorreo(), c)) {
            cambios.put("correo", true);
            u.setCorreo(c);
        }
        if (!Objects.equals(u.getTelefono(), t)) {
            cambios.put("telefono", true);
            u.setTelefono(t);
        }
        if (!Objects.equals(u.getDireccionNotificacion(), d)) {
            cambios.put("direccionNotificacion", true);
            u.setDireccionNotificacion(d);
        }
    }

    private UsuarioVista vista(Usuario u, String advertencia) {
        String cuenta = usuarios.estadoCuentaPortal(u.getId()).map(Enum::name).orElse("SIN_CUENTA");
        return vista(u, catalogo.nombresMunicipio(), cuenta, advertencia);
    }

    private static UsuarioVista vista(Usuario u, Map<Long, String> municipios, String cuentaPortal, String advertencia) {
        return new UsuarioVista(u.getId(), u.getCodigo(), u.getTipoPersona(), u.getTipoIdentificacion(),
                u.getNumeroIdentificacionMascara(), u.getNombreCompleto(), u.getCorreo(), u.getTelefono(),
                u.getDireccionNotificacion(), u.getMunicipioId(), municipios.get(u.getMunicipioId()),
                u.getEstadoVerificacionRnec(), u.getFechaVerificacionRnec(), Boolean.TRUE.equals(u.getActivo()),
                u.getFechaInactivacion(), u.getMotivoInactivacion(), cuentaPortal, u.getCreadoEn(), u.getVersion(),
                advertencia);
    }
}
