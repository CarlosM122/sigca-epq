package co.gov.epq.sigca.portal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.cartera.AcuerdoPago;
import co.gov.epq.sigca.cartera.CuotaAcuerdo;
import co.gov.epq.sigca.catalogo.MunicipioRepository;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.contratos.ContratoServicio;
import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.contratos.Medidor;
import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.facturacion.FacturaDetalle;
import co.gov.epq.sigca.facturacion.FacturaService;
import co.gov.epq.sigca.facturacion.FacturacionContratosRepository;
import co.gov.epq.sigca.facturacion.FacturacionMedidoresRepository;
import co.gov.epq.sigca.facturacion.FacturacionPrediosRepository;
import co.gov.epq.sigca.lecturas.EstadoLectura;
import co.gov.epq.sigca.lecturas.Lectura;
import co.gov.epq.sigca.pagos.Obligacion;
import co.gov.epq.sigca.pagos.Obligaciones;
import co.gov.epq.sigca.pagos.PagoResumen;
import co.gov.epq.sigca.pagos.PagoService;
import co.gov.epq.sigca.pagos.PagosAcuerdosRepository;
import co.gov.epq.sigca.pagos.PagosCuotasRepository;
import co.gov.epq.sigca.pagos.TipoObligacion;
import co.gov.epq.sigca.portal.PortalVistas.AcuerdoPortal;
import co.gov.epq.sigca.portal.PortalVistas.ConsumoPortal;
import co.gov.epq.sigca.portal.PortalVistas.ConsumosContrato;
import co.gov.epq.sigca.portal.PortalVistas.ContratoPortal;
import co.gov.epq.sigca.portal.PortalVistas.CuotaPortal;
import co.gov.epq.sigca.usuarios.Predio;

/**
 * Consultas del portal ciudadano (RF-07, RF-26, RF-27). Todo se filtra por el titular autenticado: lo ajeno responde
 * 404, sin revelar si existe.
 */
@Service
@Transactional(readOnly = true)
public class PortalService {

    private final PortalContratosRepository contratos;
    private final PortalLecturasRepository lecturas;
    private final FacturacionContratosRepository contratosFacturacion;
    private final FacturacionPrediosRepository predios;
    private final FacturacionMedidoresRepository medidores;
    private final MunicipioRepository municipios;
    private final FacturaService facturas;
    private final PagoService pagos;
    private final PagosAcuerdosRepository acuerdos;
    private final PagosCuotasRepository cuotas;
    private final Obligaciones obligaciones;
    private final Parametros parametros;

    public PortalService(PortalContratosRepository contratos, PortalLecturasRepository lecturas,
            FacturacionContratosRepository contratosFacturacion, FacturacionPrediosRepository predios,
            FacturacionMedidoresRepository medidores, MunicipioRepository municipios, FacturaService facturas,
            PagoService pagos, PagosAcuerdosRepository acuerdos, PagosCuotasRepository cuotas, Obligaciones obligaciones,
            Parametros parametros) {
        this.contratos = contratos;
        this.lecturas = lecturas;
        this.contratosFacturacion = contratosFacturacion;
        this.predios = predios;
        this.medidores = medidores;
        this.municipios = municipios;
        this.facturas = facturas;
        this.pagos = pagos;
        this.acuerdos = acuerdos;
        this.cuotas = cuotas;
        this.obligaciones = obligaciones;
        this.parametros = parametros;
    }

    /** P-29: el portal muestra los contratos activos y suspendidos. */
    public List<ContratoPortal> contratos(Long usuarioId) {
        return contratos.findByUsuarioIdAndEstadoInOrderByPredioIdAscTipoServicioAsc(usuarioId,
                List.of(EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO)).stream().map(c -> {
            Predio p = predios.findById(c.getPredioId()).orElseThrow();
            String municipio = municipios.findById(p.getMunicipioId()).map(m -> m.getNombre()).orElse(null);
            return new ContratoPortal(c.getId(), c.getNumeroContrato(), c.getTipoServicio(), c.getEstado(),
                    c.getFechaVinculacion(), p.getId(), p.getCodigoPredio(), p.getDireccion(), municipio,
                    p.getUso() == null ? null : p.getUso().name(), p.getEstrato(), serial(c.getMedidorId()));
        }).toList();
    }

    public List<FacturaDetalle> facturas(Long usuarioId) {
        return facturas.delUsuario(usuarioId);
    }

    public FacturaDetalle factura(Long usuarioId, Long facturaId) {
        FacturaDetalle f = facturas.detalle(facturaId);
        if (!Objects.equals(f.usuarioId(), usuarioId)) {
            throw new RecursoNoEncontradoException("Factura", facturaId);
        }
        return f;
    }

    /** RF-27: consumos de los últimos HISTORIAL_MESES meses del medidor del contrato. */
    public ConsumosContrato consumos(Long usuarioId, Long contratoId) {
        ContratoServicio c = contratos.findByIdAndUsuarioId(contratoId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contrato", contratoId));
        Long medidorId = c.getMedidorId();
        String nota = null;
        if (c.getTipoServicio() == TipoServicio.ALCANTARILLADO) {
            medidorId = contratosFacturacion.findByPredioIdAndTipoServicioAndEstadoIn(c.getPredioId(),
                    TipoServicio.ACUEDUCTO, List.of(EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO)).stream()
                    .map(ContratoServicio::getMedidorId).filter(Objects::nonNull).findFirst().orElse(null);
            nota = "El alcantarillado se liquida con el consumo del medidor de acueducto del predio.";
        }
        if (medidorId == null) {
            return new ConsumosContrato(c.getId(), c.getNumeroContrato(), c.getTipoServicio(), null,
                    "El contrato no tiene medidor asociado.", null, List.of());
        }
        int meses = parametros.entero(Parametros.HISTORIAL_MESES);
        int desde = Periodos.dePeriodo(Periodos.aMes(Periodos.actual()).minusMonths(meses));
        List<ConsumoPortal> lista = lecturas.findByMedidorIdAndPeriodoGreaterThanEqualOrderByPeriodoDesc(medidorId, desde)
                .stream().map(PortalService::consumo).toList();
        List<BigDecimal> validos = lista.stream()
                .filter(l -> l.estado() == EstadoLectura.CONFIRMADA || l.estado() == EstadoLectura.ESTIMADA)
                .map(ConsumoPortal::consumo).filter(Objects::nonNull).toList();
        BigDecimal promedio = validos.isEmpty() ? null
                : validos.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(validos.size()), 2, RoundingMode.HALF_UP);
        return new ConsumosContrato(c.getId(), c.getNumeroContrato(), c.getTipoServicio(), serial(medidorId), nota,
                promedio, lista);
    }

    private static ConsumoPortal consumo(Lectura l) {
        return new ConsumoPortal(l.getPeriodo(), l.getFechaLectura(), l.getLecturaAnterior(), l.getLecturaActual(),
                l.getConsumo(), l.getEstado(), l.getOrigen());
    }

    public List<AcuerdoPortal> acuerdos(Long usuarioId) {
        return acuerdos.findByUsuarioIdOrderByFechaAcuerdoDescIdDesc(usuarioId).stream().map(this::acuerdo).toList();
    }

    private AcuerdoPortal acuerdo(AcuerdoPago a) {
        List<CuotaPortal> cs = cuotas.findByAcuerdoIdOrderByNumeroCuota(a.getId()).stream().map(c -> cuota(c, a)).toList();
        return new AcuerdoPortal(a.getId(), a.getNumeroAcuerdo(), a.getFechaAcuerdo(), a.getTotalIncluido(),
                a.getCuotaInicial(), a.getNumeroCuotas(), a.getEstado(), cs);
    }

    private CuotaPortal cuota(CuotaAcuerdo c, AcuerdoPago a) {
        Obligacion o = obligaciones.porId(TipoObligacion.CUOTA, c.getId());
        return new CuotaPortal(c.getId(), c.getNumeroCuota(), o.referencia(), c.getValorCuota(), c.getFechaVencimiento(),
                c.getEstado(), c.getFechaPago(), o.admitePago(), o.admitePago() ? null : o.motivoNoAdmite());
    }

    public Page<PagoResumen> pagos(Long usuarioId, int pagina, int tamano) {
        return pagos.buscar(new PagoService.Filtro(null, null, usuarioId, null, null, null, null), pagina, tamano);
    }

    /** Verifica que el pago sea del titular antes de entregar el comprobante. */
    public String codigoPagoPropio(Long usuarioId, Long pagoId) {
        var p = pagos.obtener(pagoId);
        if (!Objects.equals(p.getUsuarioId(), usuarioId)) {
            throw new RecursoNoEncontradoException("Pago", pagoId);
        }
        return p.getCodigoPago();
    }

    private String serial(Long medidorId) {
        return medidorId == null ? null : medidores.findById(medidorId).map(Medidor::getSerial).orElse(null);
    }
}
