package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.error.TraductorErroresOracle;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import co.gov.epq.sigca.usuarios.Predio;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lote mensual de facturación (RF-05, CU-03, SWR-15). Cada factura va en su propia transacción: un predio con
 * error (lectura en revisión, sin tarifa…) queda en DETALLE_ERRORES y no detiene el lote. Relanzar el periodo
 * factura solo lo pendiente; un lote con errores termina FALLIDO, lo que permite relanzarlo (UX_LOTE_PERIODO).
 */
@Service
public class LoteFacturacionService {

    private static final Logger log = LoggerFactory.getLogger(LoteFacturacionService.class);

    private final LoteFacturacionRepository lotes;
    private final FacturacionContratosRepository contratos;
    private final FacturacionPrediosRepository predios;
    private final EmisorFacturas emisor;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final JsonMapper json;
    private final TransactionTemplate nuevaTx;

    public LoteFacturacionService(LoteFacturacionRepository lotes, FacturacionContratosRepository contratos,
            FacturacionPrediosRepository predios, EmisorFacturas emisor, Parametros parametros, Auditoria auditoria,
            JsonMapper json, PlatformTransactionManager tm) {
        this.lotes = lotes;
        this.contratos = contratos;
        this.predios = predios;
        this.emisor = emisor;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.json = json;
        this.nuevaTx = new TransactionTemplate(tm);
        this.nuevaTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Lanza el lote de todo el periodo (por defecto el último cerrado). */
    public LoteVista ejecutar(Integer periodo) {
        return ejecutar(periodo == null ? Periodos.anterior() : periodo, null);
    }

    /**
     * @param soloPredios si no es null, limita el lote a esos predios (uso interno y pruebas de integración sobre la
     *                    BD compartida); el lote mensual siempre pasa null.
     */
    public LoteVista ejecutar(int periodo, Collection<Long> soloPredios) {
        validarPeriodo(periodo);
        String actor = UsuarioActual.usernameOSistema();
        LocalDateTime inicio = LocalDateTime.now(Periodos.ZONA);
        Long loteId = nuevaTx.execute(s -> {
            if (lotes.existsByPeriodoAndEstadoIn(periodo, List.of(EstadoLote.EN_PROCESO, EstadoLote.COMPLETADO))) {
                throw new ConflictoException("LOTE_EXISTENTE", "El periodo " + periodo
                        + " ya tiene un lote en proceso o completado; solo se relanza un lote FALLIDO");
            }
            LoteFacturacion l = new LoteFacturacion();
            l.setPeriodo(periodo);
            l.setEstado(EstadoLote.EN_PROCESO);
            l.setIniciadoEn(inicio);
            l.setTotalPredios(0L);
            l.setTotalFacturas(0L);
            l.setTotalErrores(0L);
            l.setValorTotal(BigDecimal.ZERO);
            l.setEjecutadoPor(actor);
            return lotes.saveAndFlush(l).getId();
        });

        try {
            return procesar(loteId, periodo, soloPredios, inicio);
        } catch (RuntimeException e) {
            // Un fallo inesperado no deja el lote EN_PROCESO bloqueando el periodo
            log.error("El lote {} del periodo {} falló", loteId, periodo, e);
            cerrar(loteId, periodo, 0, 0, List.of(new LoteVista.ErrorPredio(null, null, null, mensaje(e))),
                    BigDecimal.ZERO, true);
            throw e;
        }
    }

    private LoteVista procesar(Long loteId, int periodo, Collection<Long> soloPredios, LocalDateTime inicio) {
        List<ClaveFactura> pendientes = contratos.pendientesDeFacturar(periodo, Periodos.fin(periodo));
        if (soloPredios != null) {
            Set<Long> filtro = Set.copyOf(soloPredios);
            pendientes = pendientes.stream().filter(c -> filtro.contains(c.predioId())).toList();
        }
        pendientes = pendientes.stream()
                .sorted(Comparator.comparing(ClaveFactura::predioId).thenComparing(ClaveFactura::usuarioId))
                .toList();

        Duration maximo = Duration.ofMinutes(parametros.entero(Parametros.LOTE_MAX_MINUTOS));
        List<LoteVista.ErrorPredio> errores = new ArrayList<>();
        long facturadas = 0;
        BigDecimal valor = BigDecimal.ZERO;
        for (ClaveFactura clave : pendientes) {
            if (Duration.between(inicio, LocalDateTime.now(Periodos.ZONA)).compareTo(maximo) > 0) {
                long faltan = pendientes.size() - facturadas - errores.size();
                errores.add(new LoteVista.ErrorPredio(null, null, null, "Se superó el tiempo máximo del lote ("
                        + maximo.toMinutes() + " min); quedaron " + faltan + " predios sin procesar. Relance el lote."));
                return cerrar(loteId, periodo, pendientes.size(), facturadas, errores, valor, true);
            }
            try {
                BigDecimal total = nuevaTx.execute(s -> {
                    Factura f = emisor.emitir(clave, periodo, loteId, null);
                    emisor.notificar(f, EventoNotificacion.FACTURA_EMITIDA);
                    return f.getTotalAPagar();
                });
                facturadas++;
                valor = valor.add(total);
            } catch (RuntimeException e) {
                errores.add(new LoteVista.ErrorPredio(clave.usuarioId(), clave.predioId(), codigoPredio(clave.predioId()),
                        mensaje(e)));
            }
        }
        return cerrar(loteId, periodo, pendientes.size(), facturadas, errores, valor, false);
    }

    /** Estado final: COMPLETADO sin errores; FALLIDO si algún predio quedó sin facturar (se puede relanzar). */
    private LoteVista cerrar(Long loteId, int periodo, long totalPredios, long totalFacturas,
            List<LoteVista.ErrorPredio> errores, BigDecimal valorTotal, boolean interrumpido) {
        long totalErrores = interrumpido ? totalPredios - totalFacturas : errores.size();
        return nuevaTx.execute(s -> {
            LoteFacturacion l = lotes.findById(loteId).orElseThrow();
            l.setTotalPredios(totalPredios);
            l.setTotalFacturas(totalFacturas);
            l.setTotalErrores(totalErrores);
            l.setValorTotal(valorTotal);
            l.setDetalleErrores(errores.isEmpty() ? null : json.writeValueAsString(errores));
            l.setEstado(totalErrores == 0 && !interrumpido ? EstadoLote.COMPLETADO : EstadoLote.FALLIDO);
            l.setFinalizadoEn(LocalDateTime.now(Periodos.ZONA));
            lotes.saveAndFlush(l);
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("periodo", periodo);
            detalle.put("estado", l.getEstado());
            detalle.put("predios", totalPredios);
            detalle.put("facturas", totalFacturas);
            detalle.put("errores", totalErrores);
            detalle.put("valorTotal", valorTotal);
            auditoria.exito(OperacionAuditoria.GENERAR_LOTE_FACTURAS, "LOTE_FACTURACION", loteId, detalle);
            log.info("Lote {} del periodo {}: {} facturas, {} errores", loteId, periodo, totalFacturas, totalErrores);
            return vista(l);
        });
    }

    /** El periodo debe estar cerrado y dentro del plazo de cobro de RN-34 (lo vuelve a exigir TRG_FACTURA_CIUD). */
    void validarPeriodo(int periodo) {
        Periodos.aMes(periodo);
        if (periodo >= Periodos.actual()) {
            throw new NegocioException("PERIODO_ABIERTO", "Solo se factura un periodo cerrado; el último es " + Periodos.anterior());
        }
        int meses = parametros.entero(Parametros.FACTURA_MESES_COBRO_OMITIDO);
        if (Periodos.hoy().isAfter(Periodos.fin(periodo).plusMonths(meses))) {
            throw new NegocioException("PLAZO_LEGAL", "Ya no se puede facturar el periodo " + periodo
                    + ": pasaron más de " + meses + " meses desde su cierre (RN-34)", HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    @Transactional(readOnly = true)
    public List<LoteVista> recientes(int cantidad) {
        return lotes.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(Math.max(cantidad, 1), 50))).stream()
                .map(this::vista).toList();
    }

    @Transactional(readOnly = true)
    public LoteVista detalle(Long id) {
        return lotes.findById(id).map(this::vista).orElseThrow(() -> new RecursoNoEncontradoException("Lote", id));
    }

    @Transactional(readOnly = true)
    public boolean completado(int periodo) {
        return lotes.existsByPeriodoAndEstadoIn(periodo, List.of(EstadoLote.EN_PROCESO, EstadoLote.COMPLETADO));
    }

    private LoteVista vista(LoteFacturacion l) {
        List<LoteVista.ErrorPredio> errores = l.getDetalleErrores() == null ? List.of()
                : json.readValue(l.getDetalleErrores(), new TypeReference<List<LoteVista.ErrorPredio>>() {
                });
        return new LoteVista(l.getId(), l.getPeriodo(), l.getEstado(), l.getIniciadoEn(), l.getFinalizadoEn(),
                l.getTotalPredios(), l.getTotalFacturas(), l.getTotalErrores(), l.getValorTotal(), l.getEjecutadoPor(),
                errores);
    }

    private String codigoPredio(Long predioId) {
        return predios.findById(predioId).map(Predio::getCodigoPredio).orElse(null);
    }

    private static String mensaje(RuntimeException e) {
        if (e instanceof NegocioException n) {
            return n.getMessage();
        }
        return TraductorErroresOracle.traducir(e).map(NegocioException::getMessage).orElseGet(() -> {
            log.error("Error inesperado facturando un predio", e);
            return "Error inesperado: " + e.getClass().getSimpleName();
        });
    }
}
