package com.epq.sigca.usuario.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.epq.sigca.common.ApiException;
import com.epq.sigca.usuario.domain.EstadoUsuario;
import com.epq.sigca.usuario.domain.TipoDocumento;
import com.epq.sigca.usuario.domain.TipoPersona;
import com.epq.sigca.usuario.domain.Usuario;
import com.epq.sigca.usuario.dto.RegistroUsuarioRequest;
import com.epq.sigca.usuario.dto.TipoDocumentoResponse;
import com.epq.sigca.usuario.dto.UsuarioResponse;
import com.epq.sigca.usuario.repository.EstadoUsuarioRepository;
import com.epq.sigca.usuario.repository.TipoDocumentoRepository;
import com.epq.sigca.usuario.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_PENDIENTE = "PENDIENTE_VALIDACION";

    private final UsuarioRepository usuarioRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final EstadoUsuarioRepository estadoUsuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidadorIdentidad validadorIdentidad;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          TipoDocumentoRepository tipoDocumentoRepository,
                          EstadoUsuarioRepository estadoUsuarioRepository,
                          PasswordEncoder passwordEncoder,
                          ValidadorIdentidad validadorIdentidad) {
        this.usuarioRepository = usuarioRepository;
        this.tipoDocumentoRepository = tipoDocumentoRepository;
        this.estadoUsuarioRepository = estadoUsuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.validadorIdentidad = validadorIdentidad;
    }

   
    @Transactional(readOnly = true)
    public List<TipoDocumentoResponse> listarTiposDocumento() {
        return tipoDocumentoRepository.findAll(Sort.by("nombre")).stream()
                .map(t -> new TipoDocumentoResponse(t.getCodigo(), t.getNombre(), t.getTipoPersona().name()))
                .toList();
    }

   
    @Transactional
    public UsuarioResponse registrar(RegistroUsuarioRequest req) {
        if (!req.password().equals(req.confirmarPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Las contraseñas no coinciden");
        }

        TipoDocumento tipoDocumento = tipoDocumentoRepository
                .findByCodigo(req.tipoDocumento().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Tipo de documento no válido"));

        validarDatosSegunTipoPersona(tipoDocumento.getTipoPersona(), req);

        String numeroDocumento = req.numeroDocumento().trim();
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un usuario registrado con ese documento");
        }
        if (usuarioRepository.existeCorreo(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ese correo electrónico ya está registrado");
        }

        OffsetDateTime ahora = OffsetDateTime.now();

        boolean identidadValidada = false;
        if (tipoDocumento.isValidaRnec()) {
            if (!validadorIdentidad.validar(tipoDocumento.getCodigo(), numeroDocumento)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No fue posible validar su identidad ante la Registraduría");
            }
            identidadValidada = true;
        }

        String codigoEstado = identidadValidada ? ESTADO_ACTIVO : ESTADO_PENDIENTE;
        EstadoUsuario estado = estadoUsuarioRepository.findByCodigo(codigoEstado)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el estado " + codigoEstado + " en ESTADO_USUARIO. Ejecute seed_f01_catalogos.sql"));

        Usuario usuario = new Usuario();
        usuario.setTipoPersona(tipoDocumento.getTipoPersona()); // lo define el catálogo, no el cliente
        usuario.setTipoDocumento(tipoDocumento);
        usuario.setNumeroDocumento(numeroDocumento);
        usuario.setNombres(limpiar(req.nombres()));
        usuario.setApellidos(limpiar(req.apellidos()));
        usuario.setRazonSocial(limpiar(req.razonSocial()));
        usuario.setEmail(email);
        usuario.setTelefono(req.telefono().trim());
        usuario.setPasswordHash(passwordEncoder.encode(req.password()));
        usuario.setEstado(estado);
        usuario.setFechaValidacionIdentidad(identidadValidada ? ahora : null);
        usuario.setFechaAceptaTratDatos(ahora); // el DTO ya garantiza aceptaTratamientoDatos = true

        try {
            // saveAndFlush envía el INSERT ya: si dos personas se registran a la vez,
            // las restricciones únicas de Oracle son la última barrera.
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw traducirViolacion(ex);
        }

        return new UsuarioResponse(
                usuario.getId(),
                tipoDocumento.getCodigo(),
                numeroDocumento,
                nombreParaMostrar(usuario),
                email,
                estado.getCodigo());
    }

   
    private void validarDatosSegunTipoPersona(TipoPersona tipo, RegistroUsuarioRequest req) {
        boolean hayNombres = StringUtils.hasText(req.nombres());
        boolean hayApellidos = StringUtils.hasText(req.apellidos());
        boolean hayRazonSocial = StringUtils.hasText(req.razonSocial());

        if (tipo == TipoPersona.NATURAL) {
            if (!hayNombres || !hayApellidos) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Para persona natural debe indicar nombres y apellidos");
            }
            if (hayRazonSocial) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Para persona natural no debe indicar razón social");
            }
        } else {
            if (!hayRazonSocial) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Para persona jurídica debe indicar la razón social");
            }
            if (hayNombres || hayApellidos) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Para persona jurídica no debe indicar nombres ni apellidos");
            }
        }
    }

   
    private ApiException traducirViolacion(DataIntegrityViolationException ex) {
        String causa = String.valueOf(ex.getMostSpecificCause().getMessage()).toUpperCase(Locale.ROOT);
        if (causa.contains("UQ_USUARIO_EMAIL")) {
            return new ApiException(HttpStatus.CONFLICT, "Ese correo electrónico ya está registrado");
        }
        if (causa.contains("UQ_USUARIO_DOCUMENTO")) {
            return new ApiException(HttpStatus.CONFLICT, "Ya existe un usuario registrado con ese documento");
        }
        if (causa.contains("ORA-02290") || causa.contains("ORA-02291")) {
            return new ApiException(HttpStatus.BAD_REQUEST, "Los datos enviados no cumplen las reglas de la base de datos");
        }
        return new ApiException(HttpStatus.CONFLICT, "No fue posible registrar el usuario por un conflicto de datos");
    }

    private String nombreParaMostrar(Usuario u) {
        return u.getTipoPersona() == TipoPersona.NATURAL
                ? u.getNombres() + " " + u.getApellidos()
                : u.getRazonSocial();
    }

   
    private String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
