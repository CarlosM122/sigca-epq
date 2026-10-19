package co.gov.epq.sigca.contratos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import co.gov.epq.sigca.contratos.ContratoDtos.OrdenAnular;
import co.gov.epq.sigca.contratos.ContratoDtos.OrdenCrear;
import co.gov.epq.sigca.contratos.ContratoDtos.OrdenEjecutar;
import co.gov.epq.sigca.contratos.ContratoDtos.OrdenVista;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import co.gov.epq.sigca.usuarios.CatalogoTerritorial;
import co.gov.epq.sigca.usuarios.Predio;
import co.gov.epq.sigca.usuarios.PredioRepository;
import co.gov.epq.sigca.usuarios.Textos;
import co.gov.epq.sigca.usuarios.Usuario;
import co.gov.epq.sigca.usuarios.UsuarioRepository;
import co.gov.epq.sigca.usuarios.Versiones;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Subquery;

/**
 * Órdenes de suspensión y reconexión (P-11, RN-36). Ejecutar una orden es la única forma de suspender o
 * reconectar un contrato: el trigger TRG_ORDEN_AU cambia el estado del contrato.
 */
@Service
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);
    static final String ENTIDAD = "ORDEN_SERVICIO";

    private final OrdenServicioRepository ordenes;
    private final ContratoServicioRepository contratos;
    private final PredioRepository predios;
    private final UsuarioRepository usuarios;
    private final CatalogoTerritorial catalogo;
    private final AvisosInternos avisos;
    private final Notificaciones notificaciones;
    private final Auditoria auditoria;
    private final EntityManager em;

    public OrdenService(OrdenServicioRepository ordenes, ContratoServicioRepository contratos, PredioRepository predios,
            UsuarioRepository usuarios, CatalogoTerritorial catalogo, AvisosInternos avisos, Notificaciones notificaciones,
            Auditoria auditoria, EntityManager em) {
        this.ordenes = ordenes;
        this.contratos = contratos;
        this.predios = predios;
        this.usuarios = usuarios;
        this.catalogo = catalogo;
        this.avisos = avisos;
        this.notificaciones = notificaciones;
        this.auditoria = auditoria;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public Page<OrdenVista> buscar(EstadoOrden estado, TipoOrden tipo, Long municipioId, Long contratoId, boolean soloVencidas,
            Pageable pageable) {
        List<Specification<OrdenServicio>> f = new ArrayList<>();
        if (estado != null) {
            f.add((r, q, cb) -> cb.equal(r.get("estado"), estado));
        }
        if (tipo != null) {
            f.add((r, q, cb) -> cb.equal(r.get("tipo"), tipo));
        }
        if (contratoId != null) {
            f.add((r, q, cb) -> cb.equal(r.get("contratoId"), contratoId));
        }
        if (municipioId != null) {
            f.add((r, q, cb) -> {
                Subquery<Long> sq = q.subquery(Long.class);
                var c = sq.from(ContratoServicio.class);
                Subquery<Long> sp = q.subquery(Long.class);
                var p = sp.from(Predio.class);
                sp.select(p.get("id")).where(cb.equal(p.get("municipioId"), municipioId));
                sq.select(c.get("id")).where(c.get("predioId").in(sp));
                return r.get("contratoId").in(sq);
            });
        }
        if (soloVencidas) {
            LocalDateTime ahora = LocalDateTime.now();
            f.add((r, q, cb) -> cb.and(cb.equal(r.get("estado"), EstadoOrden.PENDIENTE),
                    cb.lessThan(r.get("fechaLimite"), ahora)));
        }
        Pageable orden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("fechaOrden", "id"));
        Page<OrdenServicio> pagina = ordenes.findAll(Specification.allOf(f), orden);
        return new PageImpl<>(vistas(pagina.getContent()), orden, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrdenVista consultar(Long id) {
        return vistas(List.of(obtener(id))).getFirst();
    }

    /** El asesor ordena suspensiones o reconexiones por solicitud del usuario o por causa técnica. */
    @Transactional
    public OrdenVista crear(OrdenCrear req) {
        if (req.causa() != CausaOrden.SOLICITUD_USUARIO && req.causa() != CausaOrden.TECNICA) {
            throw new NegocioException("CAUSA_NO_PERMITIDA", req.causa() == CausaOrden.MORA
                    ? "La suspensión por mora la ordena el gestor de cartera desde el cobro persuasivo"
                    : "La reconexión por pago o acuerdo la ordena el sistema automáticamente", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        ContratoServicio c = contratos.findById(req.contratoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Contrato", req.contratoId()));
        if (req.tipo() == TipoOrden.SUSPENSION && c.getEstado() != EstadoContrato.ACTIVO) {
            throw new ConflictoException("ORDEN_NO_PROCEDENTE", "Solo se suspende un contrato activo");
        }
        if (req.tipo() == TipoOrden.RECONEXION && c.getEstado() != EstadoContrato.SUSPENDIDO) {
            throw new ConflictoException("ORDEN_NO_PROCEDENTE", "Solo se reconecta un contrato suspendido");
        }
        if (req.tipo() == TipoOrden.RECONEXION && suspendidoPorMora(c.getId())) {
            throw new NegocioException("RECONEXION_POR_MORA", "El contrato está suspendido por mora: se reconecta automáticamente "
                    + "cuando el usuario paga la deuda o firma un acuerdo de pago", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        OrdenServicio o = new OrdenServicio();
        o.setContratoId(c.getId());
        o.setTipo(req.tipo());
        o.setCausa(req.causa());
        o.setEstado(EstadoOrden.PENDIENTE);
        o.setObservacion(Normalizador.texto(req.observacion()));
        ordenes.saveAndFlush(o);
        auditoria.exito(req.tipo() == TipoOrden.SUSPENSION ? OperacionAuditoria.ORDENAR_SUSPENSION : OperacionAuditoria.ORDENAR_RECONEXION,
                ENTIDAD, o.getId(), Map.of("numero", o.getNumeroOrden(), "contrato", c.getNumeroContrato(), "causa", o.getCausa()));
        return vistas(List.of(o)).getFirst();
    }

    /** RN-36: la ejecuta un Asesor (o el Administrador); el trigger suspende o reconecta el contrato. */
    @Transactional
    public OrdenVista ejecutar(Long id, OrdenEjecutar req) {
        UsuarioActual actual = UsuarioActual.funcionario();
        if (!actual.tieneRol("ASESOR") && !actual.tieneRol("ADMIN")) {
            throw new NegocioException("FUNCIONARIO_SIN_ROL", "Las órdenes las ejecuta un Asesor", HttpStatus.FORBIDDEN);
        }
        OrdenServicio o = obtenerPendiente(id);
        Versiones.exigir(req.version(), o.getVersion());
        o.setEstado(EstadoOrden.EJECUTADA);
        o.setEjecutadaPor(actual.funcionarioId());
        String obs = Textos.opcional(req.observacion());
        if (obs != null) {
            o.setObservacion(obs);
        }
        ordenes.saveAndFlush(o);
        ContratoServicio c = contratos.findById(o.getContratoId()).orElseThrow();
        em.refresh(c);
        auditoria.exito(OperacionAuditoria.EJECUTAR_ORDEN, ENTIDAD, id, Map.of("numero", o.getNumeroOrden(), "tipo", o.getTipo(),
                "contrato", c.getNumeroContrato(), "estadoContrato", c.getEstado()));
        avisarTitular(o, c);
        return vistas(List.of(o)).getFirst();
    }

    @Transactional
    public OrdenVista anular(Long id, OrdenAnular req) {
        OrdenServicio o = obtenerPendiente(id);
        Versiones.exigir(req.version(), o.getVersion());
        o.setEstado(EstadoOrden.ANULADA);
        o.setMotivoAnulacion(Normalizador.texto(req.motivo()));
        ordenes.saveAndFlush(o);
        auditoria.exito(OperacionAuditoria.ANULAR_ORDEN, ENTIDAD, id, Map.of("numero", o.getNumeroOrden(), "tipo", o.getTipo()));
        return vistas(List.of(o)).getFirst();
    }

    /** Avisa a los asesores del municipio las reconexiones que pasaron su FECHA_LIMITE (una sola vez por orden). */
    @Transactional
    public int avisarReconexionesVencidas() {
        int avisadas = 0;
        for (OrdenServicio o : ordenes.reconexionesVencidasSinAviso(LocalDateTime.now(), PageRequest.of(0, 200))) {
            ContratoServicio c = contratos.findById(o.getContratoId()).orElseThrow();
            Predio p = predios.findById(c.getPredioId()).orElseThrow();
            int n = avisos.aAsesores(p.getMunicipioId(), EventoNotificacion.RECONEXION_VENCIDA,
                    "Reconexión vencida " + o.getNumeroOrden(),
                    "La orden de reconexión " + o.getNumeroOrden() + " del contrato " + c.getNumeroContrato() + " (" + p.getDireccion()
                            + ") venció el " + o.getFechaLimite() + " sin ejecutarse. Plazo legal: 24 horas (RN-36).",
                    ENTIDAD, o.getId());
            if (n > 0) {
                avisadas++;
            } else {
                log.warn("No hay asesores activos para avisar la reconexión vencida {}", o.getNumeroOrden());
            }
        }
        return avisadas;
    }

    private boolean suspendidoPorMora(Long contratoId) {
        return ordenes.causasSuspensionEjecutada(contratoId, PageRequest.of(0, 1)).stream().findFirst()
                .filter(CausaOrden.MORA::equals).isPresent();
    }

    private OrdenServicio obtener(Long id) {
        return ordenes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Orden de servicio", id));
    }

    private OrdenServicio obtenerPendiente(Long id) {
        OrdenServicio o = obtener(id);
        if (o.getEstado() != EstadoOrden.PENDIENTE) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "La orden ya está " + o.getEstado());
        }
        return o;
    }

    private void avisarTitular(OrdenServicio o, ContratoServicio c) {
        usuarios.findById(c.getUsuarioId()).ifPresent(u -> {
            boolean suspension = o.getTipo() == TipoOrden.SUSPENSION;
            notificaciones.correo(suspension ? EventoNotificacion.ORDEN_SUSPENSION : EventoNotificacion.ORDEN_RECONEXION,
                    u.getCorreo(), u.getId(),
                    (suspension ? "Servicio suspendido" : "Servicio reconectado") + " - contrato " + c.getNumeroContrato(),
                    "Estimado(a) " + u.getNombreCompleto() + ": se ejecutó la orden " + o.getNumeroOrden() + " y el servicio de "
                            + c.getTipoServicio().name().toLowerCase(Locale.ROOT) + " del contrato " + c.getNumeroContrato()
                            + " quedó " + (suspension ? "suspendido." : "reconectado."),
                    ENTIDAD, o.getId());
        });
    }

    private List<OrdenVista> vistas(Collection<OrdenServicio> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, ContratoServicio> cs = contratos.findAllById(lista.stream().map(OrdenServicio::getContratoId).distinct().toList())
                .stream().collect(Collectors.toMap(ContratoServicio::getId, Function.identity()));
        Map<Long, Predio> ps = predios.findAllById(cs.values().stream().map(ContratoServicio::getPredioId).distinct().toList())
                .stream().collect(Collectors.toMap(Predio::getId, Function.identity()));
        Map<Long, Usuario> us = usuarios.findAllById(cs.values().stream().map(ContratoServicio::getUsuarioId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        LocalDateTime ahora = LocalDateTime.now();
        return lista.stream().map(o -> {
            ContratoServicio c = cs.get(o.getContratoId());
            Predio p = ps.get(c.getPredioId());
            Usuario u = us.get(c.getUsuarioId());
            boolean vencida = o.getEstado() == EstadoOrden.PENDIENTE && o.getFechaLimite() != null && o.getFechaLimite().isBefore(ahora);
            return new OrdenVista(o.getId(), o.getNumeroOrden(), c.getId(), c.getNumeroContrato(), c.getTipoServicio(),
                    u.getNombreCompleto(), p.getDireccion(), p.getMunicipioId(), municipios.get(p.getMunicipioId()), o.getTipo(),
                    o.getCausa(), o.getFechaOrden(), o.getFechaLimite(), o.getValorCargo(), o.getEstado(), o.getFechaEjecucion(),
                    o.getEjecutadaPor(), o.getMotivoAnulacion(), o.getObservacion(), o.getCreadoPor(), vencida, o.getVersion());
        }).toList();
    }
}
