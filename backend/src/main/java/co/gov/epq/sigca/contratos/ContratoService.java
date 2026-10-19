package co.gov.epq.sigca.contratos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.contratos.ContratoDtos.Cancelacion;
import co.gov.epq.sigca.contratos.ContratoDtos.ContratoCrear;
import co.gov.epq.sigca.contratos.ContratoDtos.ContratoVista;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import co.gov.epq.sigca.usuarios.CatalogoTerritorial;
import co.gov.epq.sigca.usuarios.Predio;
import co.gov.epq.sigca.usuarios.PredioRepository;
import co.gov.epq.sigca.usuarios.Usuario;
import co.gov.epq.sigca.usuarios.UsuarioRepository;
import co.gov.epq.sigca.usuarios.Versiones;
import jakarta.persistence.criteria.Subquery;

/**
 * CU-02 Gestionar contrato (RF-04, RF-17, P-04, P-06, P-11). Un contrato se crea ACTIVO; la suspensión y la
 * reconexión solo ocurren al ejecutar una orden de servicio, y la cancelación exige no tener deuda.
 */
@Service
public class ContratoService {

    static final String ENTIDAD = "CONTRATO_SERVICIO";
    static final List<EstadoContrato> VIGENTES = List.of(EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO);

    private final ContratoServicioRepository contratos;
    private final MedidorRepository medidores;
    private final UsuarioRepository usuarios;
    private final PredioRepository predios;
    private final CatalogoTerritorial catalogo;
    private final Auditoria auditoria;

    public ContratoService(ContratoServicioRepository contratos, MedidorRepository medidores, UsuarioRepository usuarios,
            PredioRepository predios, CatalogoTerritorial catalogo, Auditoria auditoria) {
        this.contratos = contratos;
        this.medidores = medidores;
        this.usuarios = usuarios;
        this.predios = predios;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
    }

    @Transactional
    public ContratoVista crear(ContratoCrear req) {
        Usuario u = usuarios.findById(req.usuarioId()).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", req.usuarioId()));
        if (!Boolean.TRUE.equals(u.getActivo())) {
            throw new NegocioException("USUARIO_INACTIVO", "No se puede contratar con un usuario inactivo");
        }
        Predio p = predios.findById(req.predioId()).orElseThrow(() -> new RecursoNoEncontradoException("Predio", req.predioId()));
        catalogo.exigirMunicipioActivo(p.getMunicipioId());
        // RN-01: uno vigente por predio y servicio (también lo garantiza UX_CONTRATO_VIGENTE)
        if (contratos.existsByPredioIdAndTipoServicioAndEstadoIn(p.getId(), req.tipoServicio(), VIGENTES)) {
            throw new ConflictoException("DUPLICADO", "Ya existe un contrato vigente para ese predio y servicio (RN-01)");
        }
        Long medidorId = medidorParaContrato(req, p);

        ContratoServicio c = new ContratoServicio();
        c.setUsuarioId(u.getId());
        c.setPredioId(p.getId());
        c.setTipoServicio(req.tipoServicio());
        c.setMedidorId(medidorId);
        c.setFechaVinculacion(req.fechaVinculacion());
        c.setEstado(EstadoContrato.ACTIVO);
        c.setFuncionarioId(UsuarioActual.funcionario().funcionarioId());
        // El trigger compuesto valida al terminar la sentencia que el alcantarillado tenga acueducto vigente (P-06)
        contratos.saveAndFlush(c);
        auditoria.exito(OperacionAuditoria.CREAR_CONTRATO, ENTIDAD, c.getId(), Map.of("numero", c.getNumeroContrato(),
                "usuarioId", u.getId(), "predioId", p.getId(), "tipoServicio", c.getTipoServicio()));
        return vistas(List.of(c)).getFirst();
    }

    @Transactional(readOnly = true)
    public ContratoVista consultar(Long id) {
        return vistas(List.of(obtener(id))).getFirst();
    }

    @Transactional(readOnly = true)
    public Page<ContratoVista> buscar(String numero, Long usuarioId, Long predioId, Long municipioId, TipoServicio tipo,
            EstadoContrato estado, Pageable pageable) {
        List<Specification<ContratoServicio>> f = new ArrayList<>();
        if (numero != null && !numero.isBlank()) {
            String patron = "%" + numero.trim().toUpperCase(Locale.ROOT) + "%";
            f.add((r, q, cb) -> cb.like(r.get("numeroContrato"), patron));
        }
        if (usuarioId != null) {
            f.add((r, q, cb) -> cb.equal(r.get("usuarioId"), usuarioId));
        }
        if (predioId != null) {
            f.add((r, q, cb) -> cb.equal(r.get("predioId"), predioId));
        }
        if (municipioId != null) {
            f.add((r, q, cb) -> {
                Subquery<Long> sq = q.subquery(Long.class);
                var p = sq.from(Predio.class);
                sq.select(p.get("id")).where(cb.equal(p.get("municipioId"), municipioId));
                return r.get("predioId").in(sq);
            });
        }
        if (tipo != null) {
            f.add((r, q, cb) -> cb.equal(r.get("tipoServicio"), tipo));
        }
        if (estado != null) {
            f.add((r, q, cb) -> cb.equal(r.get("estado"), estado));
        }
        Pageable orden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
        Page<ContratoServicio> pagina = contratos.findAll(Specification.allOf(f), orden);
        return new PageImpl<>(vistas(pagina.getContent()), orden, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<ContratoVista> deUsuario(Long usuarioId) {
        return vistas(contratos.findByUsuarioIdOrderByNumeroContrato(usuarioId));
    }

    @Transactional(readOnly = true)
    public List<ContratoVista> dePredio(Long predioId) {
        return vistas(contratos.findByPredioIdOrderByNumeroContrato(predioId));
    }

    /** P-11: la BD rechaza con -20012 si hay valores pendientes y con -20020 si hay órdenes pendientes. */
    @Transactional
    public ContratoVista cancelar(Long id, Cancelacion req) {
        ContratoServicio c = obtener(id);
        Versiones.exigir(req.version(), c.getVersion());
        if (c.getEstado() == EstadoContrato.CANCELADO) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "El contrato ya está cancelado");
        }
        c.setEstado(EstadoContrato.CANCELADO);
        c.setMotivoEstado(Normalizador.texto(req.motivo()));
        contratos.saveAndFlush(c);
        auditoria.exito(OperacionAuditoria.CANCELAR_CONTRATO, ENTIDAD, id, Map.of("numero", c.getNumeroContrato()));
        return vistas(List.of(c)).getFirst();
    }

    ContratoServicio obtener(Long id) {
        return contratos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Contrato", id));
    }

    /** P-06: acueducto y gas llevan el medidor activo del predio para ese servicio, no asignado a otro contrato. */
    private Long medidorParaContrato(ContratoCrear req, Predio p) {
        if (req.tipoServicio() == TipoServicio.ALCANTARILLADO) {
            if (req.medidorId() != null) {
                throw new NegocioException("VALIDACION", "El alcantarillado no lleva medidor: se liquida con el consumo del acueducto",
                        HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        Medidor m = req.medidorId() != null
                ? medidores.findById(req.medidorId()).orElseThrow(() -> new RecursoNoEncontradoException("Medidor", req.medidorId()))
                : medidores.findFirstByPredioIdAndTipoServicioAndEstado(p.getId(), req.tipoServicio(), EstadoMedidor.ACTIVO)
                        .orElseThrow(() -> new NegocioException("SIN_MEDIDOR",
                                "El predio no tiene un medidor activo de " + req.tipoServicio().name().toLowerCase(Locale.ROOT)
                                        + ": instálelo antes de registrar el contrato"));
        if (!m.getPredioId().equals(p.getId()) || m.getTipoServicio() != req.tipoServicio()) {
            throw new NegocioException("MEDIDOR_NO_CORRESPONDE", "El medidor no es de ese predio o de ese servicio");
        }
        if (m.getEstado() != EstadoMedidor.ACTIVO) {
            throw new NegocioException("MEDIDOR_RETIRADO", "El medidor está retirado");
        }
        if (contratos.existsByMedidorIdAndEstadoIn(m.getId(), VIGENTES)) {
            throw new ConflictoException("DUPLICADO", "El medidor ya está asociado a otro contrato vigente");
        }
        return m.getId();
    }

    /** Arma las vistas con tres consultas por lote (usuarios, predios y medidores). */
    List<ContratoVista> vistas(Collection<ContratoServicio> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Set<Long> uIds = new HashSet<>();
        Set<Long> pIds = new HashSet<>();
        Set<Long> mIds = new HashSet<>();
        for (ContratoServicio c : lista) {
            uIds.add(c.getUsuarioId());
            pIds.add(c.getPredioId());
            if (c.getMedidorId() != null) {
                mIds.add(c.getMedidorId());
            }
        }
        Map<Long, Usuario> us = usuarios.findAllById(uIds).stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<Long, Predio> ps = predios.findAllById(pIds).stream().collect(Collectors.toMap(Predio::getId, Function.identity()));
        Map<Long, Medidor> ms = medidores.findAllById(mIds).stream().collect(Collectors.toMap(Medidor::getId, Function.identity()));
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        return lista.stream().map(c -> {
            Usuario u = us.get(c.getUsuarioId());
            Predio p = ps.get(c.getPredioId());
            Medidor m = c.getMedidorId() == null ? null : ms.get(c.getMedidorId());
            return new ContratoVista(c.getId(), c.getNumeroContrato(), c.getUsuarioId(), u.getCodigo(), u.getNombreCompleto(),
                    p.getId(), p.getCodigoPredio(), p.getDireccion(), p.getMunicipioId(), municipios.get(p.getMunicipioId()),
                    c.getTipoServicio(), c.getMedidorId(), m == null ? null : m.getSerial(), c.getFechaVinculacion(),
                    c.getEstado(), c.getFechaEstado(), c.getMotivoEstado(), c.getVersion());
        }).toList();
    }
}
