package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.contratos.CausaOrden;
import co.gov.epq.sigca.contratos.EstadoOrden;
import co.gov.epq.sigca.contratos.OrdenServicio;
import co.gov.epq.sigca.contratos.TipoOrden;
import co.gov.epq.sigca.seguridad.UsuarioActual;

/**
 * Cobro persuasivo y suspensión por mora (RF-23, P-11, RN-03, RN-04): iniciar cobro, marcar candidato, avisar y
 * ordenar la suspensión. Los triggers TRG_COBRO_BIU y TRG_ORDEN_BIU son la última barrera de cada regla.
 */
@Service
public class CobroService {

    static final String ENTIDAD = "COBRO_PERSUASIVO";
    private static final List<EstadoCobro> ABIERTOS = List.of(EstadoCobro.ACTIVO, EstadoCobro.SUSPENDIDO);

    private final CarteraCobroRepository cobros;
    private final CarteraOrdenRepository ordenes;
    private final CarteraConsultas consultas;
    private final AccesoCartera acceso;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final AvisosCartera avisos;

    public CobroService(CarteraCobroRepository cobros, CarteraOrdenRepository ordenes, CarteraConsultas consultas,
            AccesoCartera acceso, Parametros parametros, Auditoria auditoria, AvisosCartera avisos) {
        this.cobros = cobros;
        this.ordenes = ordenes;
        this.consultas = consultas;
        this.acceso = acceso;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.avisos = avisos;
    }

    @Transactional(readOnly = true)
    public CarteraVistas.Cobro consultar(Long id) {
        CobroPersuasivo c = cargar(id);
        return vista(c);
    }

    /** P-11 paso 1: abre el cobro persuasivo de un usuario con facturas vencidas. */
    @Transactional
    public CarteraVistas.Cobro iniciar(CarteraSolicitudes.IniciarCobro s) {
        UsuarioActual f = UsuarioActual.funcionario();
        acceso.usuario(s.usuarioId());
        if (cobros.findFirstByUsuarioIdAndEstadoIn(s.usuarioId(), ABIERTOS).isPresent()) {
            throw new ConflictoException("COBRO_ABIERTO", "El usuario ya tiene un cobro persuasivo abierto");
        }
        CarteraConsultas.DeudaVencida d = exigirMora(s.usuarioId());
        CobroPersuasivo c = new CobroPersuasivo();
        c.setUsuarioId(s.usuarioId());
        c.setFechaInicio(Periodos.hoy());
        c.setValorDeuda(d.exigible());
        c.setPeriodosMora(d.periodos());
        c.setEstado(EstadoCobro.ACTIVO);
        c.setCandidatoSuspension(false);
        c.setFuncionarioId(f.funcionarioId());
        c.setObservacion(texto(s.observacion()));
        cobros.saveAndFlush(c);
        auditoria.exito(OperacionAuditoria.INICIAR_COBRO, ENTIDAD, c.getId(), detalle(c));
        return vista(c);
    }

    /** RN-03: candidato a suspensión con MORA_PERIODOS_SUSPENSION periodos impagos. Actualiza deuda y periodos. */
    @Transactional
    public CarteraVistas.Cobro marcarCandidato(Long id, CarteraSolicitudes.Observacion s) {
        CobroPersuasivo c = cargar(id);
        exigirActivo(c);
        if (Boolean.TRUE.equals(c.getCandidatoSuspension())) {
            throw new ConflictoException("YA_ES_CANDIDATO", "El usuario ya está marcado como candidato a suspensión");
        }
        CarteraConsultas.DeudaVencida d = exigirMora(c.getUsuarioId());
        int minimo = parametros.entero(Parametros.MORA_PERIODOS_SUSPENSION);
        if (d.periodos() < minimo) {
            throw new ConflictoException("ORDEN_NO_PROCEDENTE", "El usuario tiene " + d.periodos()
                    + " periodos en mora; se requieren " + minimo + " para ser candidato a suspensión");
        }
        c.setValorDeuda(d.exigible());
        c.setPeriodosMora(d.periodos());
        c.setCandidatoSuspension(true);
        anotar(c, s.observacion());
        cobros.saveAndFlush(c);
        auditoria.exito(OperacionAuditoria.MARCAR_CANDIDATO_SUSPENSION, ENTIDAD, c.getId(), detalle(c));
        return vista(c);
    }

    /** Aviso previo de suspensión al titular por correo y SMS. */
    @Transactional
    public CarteraVistas.Cobro avisar(Long id, CarteraSolicitudes.Observacion s) {
        CobroPersuasivo c = cargar(id);
        exigirActivo(c);
        if (!Boolean.TRUE.equals(c.getCandidatoSuspension())) {
            throw new ConflictoException("NO_ES_CANDIDATO", "Primero marque al usuario como candidato a suspensión");
        }
        if (c.getFechaAvisoSuspension() != null) {
            throw new ConflictoException("YA_AVISADO", "El aviso de suspensión ya se envió el " + c.getFechaAvisoSuspension().toLocalDate());
        }
        c.setFechaAvisoSuspension(LocalDateTime.now(Periodos.ZONA));
        anotar(c, s.observacion());
        cobros.saveAndFlush(c);
        CarteraConsultas.UsuarioCartera u = consultas.usuario(c.getUsuarioId()).orElseThrow();
        avisos.avisoSuspension(u, c);
        auditoria.exito(OperacionAuditoria.AVISAR_SUSPENSION, ENTIDAD, c.getId(), detalle(c));
        return vista(c);
    }

    /**
     * Orden de suspensión por mora (P-11) de cada contrato ACTIVO del usuario cuyo predio tiene deuda vencida,
     * omitiendo los que ya tienen una orden pendiente.
     */
    @Transactional
    public CarteraVistas.Cobro ordenarSuspension(Long id, CarteraSolicitudes.Observacion s) {
        CobroPersuasivo c = cargar(id);
        exigirActivo(c);
        if (!Boolean.TRUE.equals(c.getCandidatoSuspension()) || c.getFechaAvisoSuspension() == null) {
            throw new ConflictoException("ORDEN_NO_PROCEDENTE", "La suspensión exige un candidato notificado con el aviso previo");
        }
        if (consultas.acuerdoVigente(c.getUsuarioId()).isPresent()) {
            throw new ConflictoException("ORDEN_NO_PROCEDENTE", "El usuario tiene un acuerdo de pago vigente (P-12)");
        }
        Set<Long> conOrden = Set.copyOf(consultas.contratosConOrdenPendiente(c.getUsuarioId()));
        List<CarteraConsultas.ContratoMora> contratos = consultas.contratosActivos(c.getUsuarioId()).stream()
                .filter(CarteraConsultas.ContratoMora::conMora).filter(x -> !conOrden.contains(x.id())).toList();
        if (contratos.isEmpty()) {
            throw new ConflictoException("ORDEN_NO_PROCEDENTE",
                    "No hay contratos activos con deuda vencida sin una orden pendiente");
        }
        List<String> numeros = new ArrayList<>();
        List<String> ordenesCreadas = new ArrayList<>();
        Set<Long> municipios = new java.util.LinkedHashSet<>();
        for (CarteraConsultas.ContratoMora k : contratos) {
            OrdenServicio o = new OrdenServicio();
            o.setContratoId(k.id());
            o.setTipo(TipoOrden.SUSPENSION);
            o.setCausa(CausaOrden.MORA);
            o.setCobroId(c.getId());
            o.setEstado(EstadoOrden.PENDIENTE);
            o.setObservacion(texto(s.observacion()));
            ordenes.saveAndFlush(o);
            numeros.add(k.numero());
            ordenesCreadas.add(o.getNumeroOrden());
            municipios.add(k.municipioId());
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("orden", o.getNumeroOrden());
            d.put("contrato", k.numero());
            d.put("cobroId", c.getId());
            d.put("causa", "MORA");
            auditoria.exito(OperacionAuditoria.ORDENAR_SUSPENSION, "ORDEN_SERVICIO", o.getId(), d);
        }
        List<Long> asesores = municipios.stream().flatMap(m -> consultas.funcionariosConRol("ASESOR", m).stream())
                .distinct().toList();
        avisos.ordenSuspension(consultas.usuario(c.getUsuarioId()).orElseThrow(), numeros, c.getId(), asesores,
                ordenesCreadas);
        return vista(c);
    }

    // ------------------------------------------------------------------ apoyo

    private CobroPersuasivo cargar(Long id) {
        CobroPersuasivo c = cobros.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cobro persuasivo", id));
        acceso.usuario(c.getUsuarioId());
        return c;
    }

    private static void exigirActivo(CobroPersuasivo c) {
        if (c.getEstado() != EstadoCobro.ACTIVO) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "El cobro está " + c.getEstado());
        }
    }

    private CarteraConsultas.DeudaVencida exigirMora(Long usuarioId) {
        CarteraConsultas.DeudaVencida d = consultas.deudaVencida(usuarioId);
        if (d.facturas() == 0) {
            throw new NegocioException("SIN_MORA", "El usuario no tiene facturas vencidas", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        if (d.exigible().signum() <= 0) {
            throw new NegocioException("VALOR_EN_RECLAMO", "Toda la deuda vencida está en reclamo (RN-33)", HttpStatus.CONFLICT);
        }
        return d;
    }

    private static void anotar(CobroPersuasivo c, String observacion) {
        String t = texto(observacion);
        if (t != null) {
            c.setObservacion(t);
        }
    }

    private static String texto(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.strip();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private static Map<String, Object> detalle(CobroPersuasivo c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("usuarioId", c.getUsuarioId());
        d.put("valorDeuda", c.getValorDeuda());
        d.put("periodosMora", c.getPeriodosMora());
        d.put("candidato", Boolean.TRUE.equals(c.getCandidatoSuspension()));
        return d;
    }

    private CarteraVistas.Cobro vista(CobroPersuasivo c) {
        BigDecimal valor = c.getValorDeuda();
        return new CarteraVistas.Cobro(c.getId(), c.getUsuarioId(), c.getEstado(), c.getFechaInicio(), valor,
                c.getPeriodosMora(), Boolean.TRUE.equals(c.getCandidatoSuspension()), c.getFechaAvisoSuspension(),
                c.getAcuerdoId(), c.getObservacion(), c.getId() == null ? List.of() : consultas.ordenesDeCobro(c.getId()));
    }
}
