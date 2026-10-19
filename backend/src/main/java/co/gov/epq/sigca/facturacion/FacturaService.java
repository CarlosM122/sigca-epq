package co.gov.epq.sigca.facturacion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.catalogo.MunicipioRepository;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.error.TraductorErroresOracle;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import co.gov.epq.sigca.usuarios.Predio;
import co.gov.epq.sigca.usuarios.Usuario;

/**
 * Consulta, anulación, reexpedición (P-10) y vencimiento (RF-21) de facturas. Totales, PAGADA y las transiciones
 * las impone TRG_FACTURA_CIUD; aquí se validan antes para dar mensajes claros.
 */
@Service
public class FacturaService {

    private static final Logger log = LoggerFactory.getLogger(FacturaService.class);
    public static final int TAMANO_MAXIMO = 100;

    private final FacturaRepository facturas;
    private final LineaFacturaRepository lineas;
    private final FacturacionUsuariosRepository usuarios;
    private final FacturacionPrediosRepository predios;
    private final MunicipioRepository municipios;
    private final EmisorFacturas emisor;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final Notificaciones notificaciones;
    private final CalendarioHabil calendario;
    private final TransactionTemplate nuevaTx;

    public FacturaService(FacturaRepository facturas, LineaFacturaRepository lineas, FacturacionUsuariosRepository usuarios,
            FacturacionPrediosRepository predios, MunicipioRepository municipios, EmisorFacturas emisor,
            Parametros parametros, Auditoria auditoria, Notificaciones notificaciones, CalendarioHabil calendario,
            PlatformTransactionManager tm) {
        this.facturas = facturas;
        this.lineas = lineas;
        this.usuarios = usuarios;
        this.predios = predios;
        this.municipios = municipios;
        this.emisor = emisor;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.calendario = calendario;
        this.nuevaTx = new TransactionTemplate(tm);
        this.nuevaTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Filtro(String numero, String usuario, String predio, Integer periodo, EstadoFactura estado,
            Long municipioId, Long loteId) {
    }

    @Transactional(readOnly = true)
    public Page<FacturaResumen> buscar(Filtro f, int pagina, int tamano) {
        return facturas.buscar(mayus(f.numero()), mayus(f.usuario()), mayus(f.predio()), f.periodo(), f.estado(),
                f.municipioId(), f.loteId(), PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO)));
    }

    @Transactional(readOnly = true)
    public FacturaDetalle detalle(Long id) {
        return detalle(obtener(id));
    }

    @Transactional(readOnly = true)
    public Factura obtener(Long id) {
        return facturas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Factura", id));
    }

    /** Facturas del titular en los últimos HISTORIAL_MESES meses (portal, RF-07). */
    @Transactional(readOnly = true)
    public List<FacturaDetalle> delUsuario(Long usuarioId) {
        int meses = parametros.entero(Parametros.HISTORIAL_MESES);
        int desde = Periodos.dePeriodo(Periodos.aMes(Periodos.actual()).minusMonths(meses));
        return facturas.findByUsuarioIdAndPeriodoGreaterThanEqualOrderByPeriodoDescIdDesc(usuarioId, desde).stream()
                .map(this::detalle).toList();
    }

    @Transactional(readOnly = true)
    public FacturaDetalle detalle(Factura f) {
        Usuario u = usuarios.findById(f.getUsuarioId()).orElseThrow();
        Predio p = predios.findById(f.getPredioId()).orElseThrow();
        String municipio = municipios.findById(p.getMunicipioId()).map(m -> m.getNombre()).orElse(null);
        List<LineaDetalle> ls = lineas.detalle(f.getId());
        String reexpedicionDe = f.getReexpedicionDeId() == null ? null
                : facturas.findById(f.getReexpedicionDeId()).map(Factura::getNumeroFactura).orElse(null);
        String reexpedidaComo = facturas.findByReexpedicionDeId(f.getId()).map(Factura::getNumeroFactura).orElse(null);
        boolean estimado = ls.stream().anyMatch(l -> Boolean.TRUE.equals(l.consumoEstimado()));
        return new FacturaDetalle(f.getId(), f.getNumeroFactura(), f.getNumeroFactura(), f.getPeriodo(), u.getId(),
                u.getCodigo(), u.getNombreCompleto(), p.getId(), p.getCodigoPredio(), p.getDireccion(), municipio,
                p.getUso() == null ? null : p.getUso().name(), p.getEstrato(), f.getFechaEmision(), f.getFechaVencimiento(),
                f.getNumeroLineas(), f.getTotalAPagar(), f.getValorAjustes(), f.getValorPagado(), f.getValorEnReclamo(),
                f.getSaldoPendiente(), f.getSaldoPendiente().subtract(f.getValorEnReclamo()), f.getEstado(),
                f.getMotivoAnulacion(), reexpedicionDe, reexpedidaComo, f.getLoteId(), f.getEstadoSyncErp(), estimado, ls,
                f.getVersion());
    }

    /** Resultado de anular y, si se pidió, reexpedir. */
    public record ResultadoAnulacion(FacturaDetalle anulada, FacturaDetalle reexpedida) {
    }

    /**
     * P-10: el Asesor anula con motivo una factura PENDIENTE o VENCIDA sin pagos ni ajustes. Con
     * {@code reexpedir} emite en la misma transacción la factura corregida del mismo predio y periodo.
     */
    @Transactional
    public ResultadoAnulacion anular(Long id, String motivo, boolean reexpedir, Long version) {
        UsuarioActual funcionario = UsuarioActual.funcionario();
        Factura f = obtener(id);
        if (version != null && !version.equals(f.getVersion())) {
            throw new ConflictoException("MODIFICACION_CONCURRENTE",
                    "La factura cambió mientras la revisaba (por ejemplo, recibió un pago). Recárguela");
        }
        if (f.getEstado() != EstadoFactura.PENDIENTE && f.getEstado() != EstadoFactura.VENCIDA) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA",
                    "Solo se anulan facturas PENDIENTES o VENCIDAS; la factura está " + f.getEstado());
        }
        if (f.getValorPagado().signum() != 0 || f.getValorAjustes().signum() != 0) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "No se anula una factura con pagos o ajustes aplicados (P-10)");
        }
        String m = motivo == null ? "" : motivo.trim();
        f.setEstado(EstadoFactura.ANULADA);
        f.setMotivoAnulacion(m);
        f.setAnuladaPor(funcionario.funcionarioId());
        facturas.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.ANULAR_FACTURA, "FACTURA", f.getId(),
                Map.of("numeroFactura", f.getNumeroFactura(), "periodo", f.getPeriodo(), "motivo", m));
        FacturaDetalle reexpedida = reexpedir ? detalle(reexpedirAnulada(f)) : null;
        return new ResultadoAnulacion(detalle(f), reexpedida);
    }

    /** P-10: reexpide una factura ya anulada (una sola vez, dentro de FACTURA_MESES_COBRO_OMITIDO). */
    @Transactional
    public FacturaDetalle reexpedir(Long id) {
        UsuarioActual.funcionario();
        return detalle(reexpedirAnulada(obtener(id)));
    }

    private Factura reexpedirAnulada(Factura anulada) {
        if (anulada.getEstado() != EstadoFactura.ANULADA) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "Solo se reexpide una factura anulada");
        }
        if (facturas.findByReexpedicionDeId(anulada.getId()).isPresent()) {
            throw new ConflictoException("DUPLICADO", "La factura " + anulada.getNumeroFactura() + " ya fue reexpedida");
        }
        int meses = parametros.entero(Parametros.FACTURA_MESES_COBRO_OMITIDO);
        if (Periodos.hoy().isAfter(anulada.getFechaEmision().plusMonths(meses))) {
            throw new NegocioException("PLAZO_LEGAL", "Pasaron más de " + meses
                    + " meses desde la emisión: ya no se puede reexpedir (RN-34)");
        }
        Factura nueva = emisor.emitir(new ClaveFactura(anulada.getUsuarioId(), anulada.getPredioId()),
                anulada.getPeriodo(), null, anulada.getId());
        emisor.notificar(nueva, EventoNotificacion.FACTURA_REEXPEDIDA);
        auditoria.exito(OperacionAuditoria.REEXPEDIR_FACTURA, "FACTURA", nueva.getId(),
                Map.of("numeroFactura", nueva.getNumeroFactura(), "reexpideA", anulada.getNumeroFactura(),
                        "total", nueva.getTotalAPagar()));
        return nueva;
    }

    /**
     * RF-21: pasa a VENCIDA las PENDIENTES con vencimiento cumplido y valor exigible. Las que tienen todo el saldo en
     * reclamo no vencen (la BD lo rechaza, RN-33). Cada factura en su transacción.
     */
    public int vencerFacturas() {
        List<Long> ids = facturas.porVencer(Periodos.hoy());
        int vencidas = 0;
        for (Long id : ids) {
            try {
                Boolean ok = nuevaTx.execute(s -> vencer(id));
                if (Boolean.TRUE.equals(ok)) {
                    vencidas++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer la factura {}: {}", id,
                        TraductorErroresOracle.traducir(e).map(NegocioException::getMessage).orElse(e.getMessage()));
            }
        }
        if (vencidas > 0) {
            log.info("{} facturas pasaron a VENCIDA", vencidas);
        }
        return vencidas;
    }

    private boolean vencer(Long id) {
        Factura f = facturas.findById(id).orElseThrow();
        if (f.getEstado() != EstadoFactura.PENDIENTE || !f.getFechaVencimiento().isBefore(Periodos.hoy())
                || f.getSaldoPendiente().subtract(f.getValorEnReclamo()).signum() <= 0) {
            return false;
        }
        f.setEstado(EstadoFactura.VENCIDA);
        facturas.saveAndFlush(f);
        Usuario u = usuarios.findById(f.getUsuarioId()).orElseThrow();
        notificaciones.correo(EventoNotificacion.FACTURA_VENCIDA, u.getCorreo(), u.getId(),
                "Factura vencida " + f.getNumeroFactura(),
                "Hola " + u.getNombreCompleto() + ",\n\nSu factura " + f.getNumeroFactura() + " del periodo " + f.getPeriodo()
                        + " venció el " + f.getFechaVencimiento() + " con un saldo exigible de "
                        + EmisorFacturas.pesos(f.getSaldoPendiente().subtract(f.getValorEnReclamo()))
                        + ".\nPáguela en el portal o en nuestras oficinas para evitar la suspensión del servicio.\n",
                "FACTURA", f.getId());
        auditoria.exito(OperacionAuditoria.VENCER_FACTURA, "FACTURA", f.getId(),
                Map.of("numeroFactura", f.getNumeroFactura(), "fechaVencimiento", f.getFechaVencimiento().toString()));
        return true;
    }

    /**
     * Fecha estimada en que estará disponible la próxima factura (P-26): la del último periodo cerrado, N días hábiles
     * después del primer día hábil del mes; si esa fecha ya pasó, la del periodo en curso, que se emite el mes siguiente.
     */
    @Transactional(readOnly = true)
    public LocalDate fechaEstimadaDisponibilidad() {
        int dias = Integer.parseInt(parametros.texto("FACTURA_DIAS_HABILES_DISPONIBILIDAD"));
        YearMonth mes = Periodos.aMes(Periodos.actual());
        LocalDate fecha = calendario.sumarHabiles(calendario.primerDiaHabil(mes), dias);
        if (fecha.isBefore(Periodos.hoy())) {
            fecha = calendario.sumarHabiles(calendario.primerDiaHabil(mes.plusMonths(1)), dias);
        }
        return fecha;
    }

    private static String mayus(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }
}
