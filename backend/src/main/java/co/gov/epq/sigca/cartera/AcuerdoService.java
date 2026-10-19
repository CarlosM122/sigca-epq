package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import co.gov.epq.sigca.seguridad.UsuarioActual;

/**
 * Acuerdos de pago (RF-23, P-12, RN-07, RN-30, RN-33, RN-38). El acuerdo, sus facturas y sus cuotas se insertan en una
 * sola transacción: CK_ACUERDO_CUADRE (diferida) verifica al confirmar que todo cuadra con la deuda total.
 */
@Service
public class AcuerdoService {

    static final String ENTIDAD = "ACUERDO_PAGO";
    static final int MIN_CUOTAS = 2;

    private final CarteraAcuerdoRepository acuerdos;
    private final CarteraAcuerdoFacturaRepository acuerdoFacturas;
    private final CarteraCuotaRepository cuotas;
    private final CarteraConsultas consultas;
    private final AccesoCartera acceso;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final AvisosCartera avisos;

    public AcuerdoService(CarteraAcuerdoRepository acuerdos, CarteraAcuerdoFacturaRepository acuerdoFacturas,
            CarteraCuotaRepository cuotas, CarteraConsultas consultas, AccesoCartera acceso, Parametros parametros,
            Auditoria auditoria, AvisosCartera avisos) {
        this.acuerdos = acuerdos;
        this.acuerdoFacturas = acuerdoFacturas;
        this.cuotas = cuotas;
        this.consultas = consultas;
        this.acceso = acceso;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.avisos = avisos;
    }

    /** Facturas vencidas del usuario y los topes vigentes para armar el acuerdo. */
    @Transactional(readOnly = true)
    public CarteraVistas.DeudaUsuario deuda(Long usuarioId) {
        CarteraConsultas.UsuarioCartera u = acceso.usuario(usuarioId);
        List<CarteraVistas.FacturaVencida> facturas = consultas.facturasVencidas(usuarioId);
        List<Long> incluibles = facturas.stream().filter(CarteraVistas.FacturaVencida::incluible)
                .map(CarteraVistas.FacturaVencida::id).toList();
        BigDecimal total = facturas.stream().filter(CarteraVistas.FacturaVencida::incluible)
                .map(CarteraVistas.FacturaVencida::saldoPendiente).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CarteraVistas.DeudaUsuario(u.id(), u.codigo(), u.nombre(), facturas, total,
                incluibles.isEmpty() ? tasaUsura() : tasaMaxima(incluibles), tasaUsura(),
                parametros.decimal(Parametros.ACUERDO_CUOTA_INICIAL_PCT), MIN_CUOTAS, maxCuotas(),
                consultas.acuerdoVigente(usuarioId).orElse(null));
    }

    @Transactional(readOnly = true)
    public CarteraVistas.Simulacion simular(CarteraSolicitudes.AcuerdoDatos d) {
        return preparar(d).simulacion();
    }

    @Transactional
    public CarteraVistas.AcuerdoDetalle crear(CarteraSolicitudes.AcuerdoDatos d) {
        UsuarioActual f = UsuarioActual.funcionario();
        Preparado p = preparar(d);
        if (consultas.acuerdoVigente(d.usuarioId()).isPresent()) {
            throw new ConflictoException("ACUERDO_VIGENTE", "El usuario ya tiene un acuerdo de pago vigente");
        }
        AcuerdoPago a = new AcuerdoPago();
        a.setUsuarioId(d.usuarioId());
        a.setFechaAcuerdo(p.fecha());
        a.setTotalDeuda(p.plan().totalDeuda());
        a.setCuotaInicial(p.plan().cuotaInicial());
        a.setTasaInteresAnual(d.tasaInteresAnual());
        a.setNumeroCuotas(d.numeroCuotas());
        a.setEstado(EstadoAcuerdo.VIGENTE);
        a.setFuncionarioId(f.funcionarioId());
        acuerdos.saveAndFlush(a);

        for (CarteraVistas.FacturaVencida fv : p.facturas()) {
            AcuerdoFactura af = new AcuerdoFactura();
            af.setAcuerdoId(a.getId());
            af.setFacturaId(fv.id());
            af.setValorIncluido(fv.saldoPendiente());
            acuerdoFacturas.save(af);
        }
        acuerdoFacturas.flush();
        for (CalculadoraCuotas.Cuota c : p.plan().cuotas()) {
            CuotaAcuerdo q = new CuotaAcuerdo();
            q.setAcuerdoId(a.getId());
            q.setNumeroCuota(c.numero());
            q.setValorCapital(c.capital());
            q.setValorInteres(c.interes());
            q.setValorCuota(c.valor());
            q.setFechaVencimiento(c.fechaVencimiento());
            q.setEstado(EstadoCuota.PENDIENTE);
            cuotas.save(q);
        }
        cuotas.flush();

        Map<String, Object> det = new LinkedHashMap<>();
        det.put("numero", a.getNumeroAcuerdo());
        det.put("usuarioId", a.getUsuarioId());
        det.put("facturas", p.facturas().size());
        det.put("totalDeuda", a.getTotalDeuda());
        det.put("cuotaInicial", a.getCuotaInicial());
        det.put("numeroCuotas", a.getNumeroCuotas());
        det.put("tasaAnual", a.getTasaInteresAnual());
        auditoria.exito(OperacionAuditoria.CREAR_ACUERDO, ENTIDAD, a.getId(), det);
        avisos.acuerdoCreado(p.usuario(), a, p.plan(), a.getNumeroAcuerdo());
        return detalle(a.getId());
    }

    @Transactional(readOnly = true)
    public Page<CarteraVistas.Acuerdo> listar(EstadoAcuerdo estado, Long usuarioId, String texto, Pageable pageable) {
        Long municipio = acceso.municipioEfectivo(null);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        long total = consultas.contarAcuerdos(estado, usuarioId, texto, municipio);
        List<CarteraVistas.Acuerdo> lista = consultas.acuerdos(estado, usuarioId, texto, municipio,
                (long) pageable.getPageNumber() * size, size);
        return new PageImpl<>(lista, org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), size), total);
    }

    @Transactional(readOnly = true)
    public CarteraVistas.AcuerdoDetalle consultar(Long id) {
        CarteraVistas.AcuerdoDetalle d = detalle(id);
        acceso.usuario(d.acuerdo().usuarioId());
        return d;
    }

    /** Anulación de un acuerdo VIGENTE con motivo: la BD devuelve las facturas a VENCIDA y reactiva el cobro. */
    @Transactional
    public CarteraVistas.AcuerdoDetalle anular(Long id, CarteraSolicitudes.Anulacion s) {
        AcuerdoPago a = acuerdos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Acuerdo de pago", id));
        acceso.usuario(a.getUsuarioId());
        if (a.getEstado() != EstadoAcuerdo.VIGENTE) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "Solo se anula un acuerdo VIGENTE; este está " + a.getEstado());
        }
        a.setEstado(EstadoAcuerdo.ANULADO);
        a.setMotivoEstado(s.motivo().strip());
        acuerdos.saveAndFlush(a);
        Map<String, Object> det = new LinkedHashMap<>();
        det.put("numero", a.getNumeroAcuerdo());
        det.put("usuarioId", a.getUsuarioId());
        auditoria.exito(OperacionAuditoria.ANULAR_ACUERDO, ENTIDAD, a.getId(), det);
        return detalle(a.getId());
    }

    // ------------------------------------------------------------------ apoyo

    private record Preparado(CarteraConsultas.UsuarioCartera usuario, List<CarteraVistas.FacturaVencida> facturas,
            LocalDate fecha, BigDecimal minimaInicial, BigDecimal tasaMaxima, CalculadoraCuotas.Plan plan) {

        CarteraVistas.Simulacion simulacion() {
            return new CarteraVistas.Simulacion(usuario.id(), facturas.stream().map(CarteraVistas.FacturaVencida::id).toList(),
                    plan.totalDeuda(), plan.cuotaInicial(), minimaInicial, plan.tasaAnual(), tasaMaxima, plan.tasaMensual(),
                    plan.numeroCuotas(), plan.saldoFinanciado(), plan.cuotaFija(), plan.totalIntereses(), plan.totalAPagar(),
                    plan.cuotas().stream().map(c -> new CarteraVistas.CuotaPlan(c.numero(), c.fechaVencimiento(), c.capital(),
                            c.interes(), c.valor(), c.saldoDespues())).toList());
        }
    }

    /** Valida facturas, topes y condiciones antes de llegar a la BD y calcula el plan. */
    private Preparado preparar(CarteraSolicitudes.AcuerdoDatos d) {
        CarteraConsultas.UsuarioCartera u = acceso.usuario(d.usuarioId());
        if (!u.activo()) {
            throw new NegocioException("OBLIGACIONES_PENDIENTES", "El usuario está inactivo", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        Map<Long, CarteraVistas.FacturaVencida> vencidas = consultas.facturasVencidas(d.usuarioId()).stream()
                .collect(Collectors.toMap(CarteraVistas.FacturaVencida::id, Function.identity()));
        Set<Long> ids = new LinkedHashSet<>(d.facturaIds());
        for (Long id : ids) {
            CarteraVistas.FacturaVencida f = vencidas.get(id);
            if (f == null) {
                throw new NegocioException("ACUERDO_INVALIDO", "La factura " + id + " no es una factura VENCIDA del usuario (RN-30)",
                        HttpStatus.UNPROCESSABLE_CONTENT);
            }
            if (f.valorEnReclamo().signum() > 0) {
                throw new NegocioException("VALOR_EN_RECLAMO", "La factura " + f.numeroFactura()
                        + " tiene valores en reclamo; resuelva la PQR antes del acuerdo (RN-33)", HttpStatus.CONFLICT);
            }
        }
        List<CarteraVistas.FacturaVencida> facturas = ids.stream().map(vencidas::get).toList();
        BigDecimal total = facturas.stream().map(CarteraVistas.FacturaVencida::saldoPendiente)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int max = maxCuotas();
        if (d.numeroCuotas() < MIN_CUOTAS || d.numeroCuotas() > max) {
            throw invalido("El número de cuotas debe estar entre " + MIN_CUOTAS + " y " + max);
        }
        BigDecimal minima = CalculadoraCuotas.cuotaInicialMinima(total,
                parametros.decimal(Parametros.ACUERDO_CUOTA_INICIAL_PCT));
        if (d.cuotaInicial().compareTo(minima) < 0) {
            throw invalido("La cuota inicial mínima es " + AvisosCartera.pesos(minima) + " ("
                    + parametros.decimal(Parametros.ACUERDO_CUOTA_INICIAL_PCT).stripTrailingZeros().toPlainString()
                    + " % de la deuda)");
        }
        if (d.cuotaInicial().compareTo(total) >= 0) {
            throw invalido("La cuota inicial debe ser menor que la deuda; si paga todo no necesita acuerdo");
        }
        BigDecimal tope = tasaMaxima(List.copyOf(ids));
        if (d.tasaInteresAnual().compareTo(tope) > 0) {
            throw invalido("La tasa máxima aplicable es " + tope.stripTrailingZeros().toPlainString() + " % efectivo anual (RN-38)");
        }
        LocalDate hoy = Periodos.hoy();
        CalculadoraCuotas.Plan plan;
        try {
            plan = CalculadoraCuotas.calcular(total, d.cuotaInicial(), d.tasaInteresAnual(), d.numeroCuotas(), hoy);
        } catch (IllegalArgumentException e) {
            throw invalido(e.getMessage());
        }
        if (plan.cuotas().stream().anyMatch(c -> c.capital().signum() <= 0)) {
            throw invalido("El saldo a financiar es muy pequeño para ese número de cuotas");
        }
        return new Preparado(u, facturas, hoy, minima, tope, plan);
    }

    /** RN-38: el menor tope entre los usos de los predios incluidos y la tasa de usura. */
    private BigDecimal tasaMaxima(List<Long> facturaIds) {
        BigDecimal tope = tasaUsura();
        for (String uso : consultas.usosDeFacturas(facturaIds)) {
            BigDecimal t = "RESIDENCIAL".equals(uso) ? parametros.decimal(Parametros.MORA_TASA_RESIDENCIAL_ANUAL)
                    : parametros.decimal(Parametros.MORA_TASA_NO_RESIDENCIAL_ANUAL);
            tope = tope.min(t);
        }
        return tope;
    }

    private BigDecimal tasaUsura() {
        return parametros.decimal(Parametros.TASA_USURA_ANUAL);
    }

    private int maxCuotas() {
        return Math.min(parametros.entero(Parametros.ACUERDO_MAX_CUOTAS), 24);
    }

    private static NegocioException invalido(String mensaje) {
        return new NegocioException("ACUERDO_INVALIDO", mensaje, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    CarteraVistas.AcuerdoDetalle detalle(Long id) {
        CarteraVistas.Acuerdo a = consultas.acuerdo(id).orElseThrow(() -> new RecursoNoEncontradoException("Acuerdo de pago", id));
        List<CarteraVistas.CuotaVista> qs = cuotas.findByAcuerdoIdOrderByNumeroCuota(id).stream()
                .map(q -> new CarteraVistas.CuotaVista(q.getId(), q.getNumeroCuota(), q.getFechaVencimiento(),
                        q.getValorCapital(), q.getValorInteres(), q.getValorCuota(), q.getEstado(), q.getFechaPago()))
                .toList();
        return new CarteraVistas.AcuerdoDetalle(a, consultas.facturasDeAcuerdo(id), qs);
    }
}
