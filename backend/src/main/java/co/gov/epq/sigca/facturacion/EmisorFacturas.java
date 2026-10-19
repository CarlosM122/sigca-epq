package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.contratos.ContratoServicio;
import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.contratos.Medidor;
import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.facturacion.Liquidador.Liquidacion;
import co.gov.epq.sigca.lecturas.EstadoLectura;
import co.gov.epq.sigca.lecturas.Lectura;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.tarifas.FactorSubsidio;
import co.gov.epq.sigca.tarifas.Tarifa;
import co.gov.epq.sigca.usuarios.Predio;
import co.gov.epq.sigca.usuarios.Usuario;
import jakarta.persistence.EntityManager;

/**
 * Emite la factura de un titular y un predio para un periodo: una línea por contrato ACTIVO (RN-16), liquidada
 * con la tarifa y el factor vigentes al cierre del periodo y la lectura CONFIRMADA o ESTIMADA del medidor
 * (P-07, P-08). Primero prepara todas las líneas y solo si todas se pueden liquidar inserta la factura, así un
 * predio con un problema no deja facturas a medias. La BD vuelve a verificar todo (TRG_LINEA_BI).
 */
@Component
public class EmisorFacturas {

    private static final Locale CO = Locale.forLanguageTag("es-CO");

    private final FacturaRepository facturas;
    private final LineaFacturaRepository lineas;
    private final FacturacionContratosRepository contratos;
    private final FacturacionLecturasRepository lecturas;
    private final FacturacionTarifasRepository tarifas;
    private final FacturacionPrediosRepository predios;
    private final FacturacionUsuariosRepository usuarios;
    private final FacturacionMedidoresRepository medidores;
    private final Parametros parametros;
    private final Notificaciones notificaciones;
    private final EntityManager em;

    public EmisorFacturas(FacturaRepository facturas, LineaFacturaRepository lineas,
            FacturacionContratosRepository contratos, FacturacionLecturasRepository lecturas,
            FacturacionTarifasRepository tarifas, FacturacionPrediosRepository predios,
            FacturacionUsuariosRepository usuarios, FacturacionMedidoresRepository medidores, Parametros parametros,
            Notificaciones notificaciones, EntityManager em) {
        this.facturas = facturas;
        this.lineas = lineas;
        this.contratos = contratos;
        this.lecturas = lecturas;
        this.tarifas = tarifas;
        this.predios = predios;
        this.usuarios = usuarios;
        this.medidores = medidores;
        this.parametros = parametros;
        this.notificaciones = notificaciones;
        this.em = em;
    }

    /**
     * Inserta factura y líneas en la transacción actual (CK_FACTURA_COMPLETA es diferida: deben confirmarse juntas).
     *
     * @param reexpedicionDeId factura anulada que se reexpide (P-10), o null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Factura emitir(ClaveFactura clave, int periodo, Long loteId, Long reexpedicionDeId) {
        Usuario usuario = usuarios.findById(clave.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", clave.usuarioId()));
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new NegocioException("USUARIO_INACTIVO", "El titular " + usuario.getCodigo() + " está inactivo");
        }
        Predio predio = predios.findById(clave.predioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Predio", clave.predioId()));
        LocalDate hoy = Periodos.hoy();
        LocalDate fin = Periodos.fin(periodo);
        if (!hoy.isAfter(fin)) {
            throw new NegocioException("PERIODO_ABIERTO", "El periodo " + periodo + " aún no ha terminado");
        }

        List<ContratoServicio> activos = contratos.findByUsuarioIdAndPredioIdAndEstadoOrderByTipoServicio(
                usuario.getId(), predio.getId(), EstadoContrato.ACTIVO);
        if (activos.isEmpty()) {
            throw new NegocioException("SIN_CONTRATOS_ACTIVOS",
                    "El predio " + predio.getCodigoPredio() + " no tiene contratos activos del titular (RN-16)");
        }
        List<LineaFactura> nuevas = new ArrayList<>();
        for (ContratoServicio c : activos) {
            nuevas.add(liquidar(c, predio, periodo, fin));
        }

        Factura f = new Factura();
        f.setUsuarioId(usuario.getId());
        f.setPredioId(predio.getId());
        f.setPeriodo(periodo);
        f.setLoteId(loteId);
        f.setReexpedicionDeId(reexpedicionDeId);
        f.setFechaEmision(hoy);
        f.setFechaVencimiento(hoy.plusDays(parametros.entero(Parametros.FACTURA_DIAS_VENCIMIENTO)));
        f.setEstado(EstadoFactura.PENDIENTE);
        f.setEstadoSyncErp(EstadoSyncErp.PENDIENTE);
        facturas.saveAndFlush(f);
        for (LineaFactura l : nuevas) {
            l.setFacturaId(f.getId());
        }
        lineas.saveAllAndFlush(nuevas);
        // Los triggers de las líneas acumularon los totales en la factura
        em.refresh(f);
        return f;
    }

    private LineaFactura liquidar(ContratoServicio c, Predio predio, int periodo, LocalDate fin) {
        TipoServicio tipo = c.getTipoServicio();
        Tarifa tarifa = tarifas.vigentes(tipo, predio.getMunicipioId(), fin).stream().findFirst()
                .orElseThrow(() -> new NegocioException("TARIFA_NO_VIGENTE", "No hay tarifa de " + tipo
                        + " vigente al cierre del periodo " + periodo + " en el municipio del predio"));
        FactorSubsidio factor = null;
        if (tipo != TipoServicio.GAS) {
            factor = tarifas.factoresVigentes(tipo, predio.getMunicipioId(), predio.getUso(), fin).stream()
                    .filter(fs -> Objects.equals(fs.getEstrato(), predio.getEstrato()))
                    .findFirst()
                    .orElseThrow(() -> new NegocioException("FACTOR_NO_VIGENTE", "No hay factor de subsidio o contribución de "
                            + tipo + " para uso " + predio.getUso()
                            + (predio.getEstrato() != null ? " estrato " + predio.getEstrato() : "") + " vigente en " + periodo));
        }
        Long medidorId = medidorDeLectura(c);
        Lectura lectura = lecturas.findByMedidorIdAndPeriodo(medidorId, periodo)
                .orElseThrow(() -> new NegocioException("SIN_LECTURA", "El contrato " + c.getNumeroContrato()
                        + " no tiene lectura del periodo " + periodo + " (medidor " + serial(medidorId) + ")"));
        if (lectura.getEstado() != EstadoLectura.CONFIRMADA && lectura.getEstado() != EstadoLectura.ESTIMADA) {
            throw new NegocioException("LECTURA_NO_FACTURABLE", "La lectura " + periodo + " del medidor " + serial(medidorId)
                    + " está " + lectura.getEstado() + " y no se puede facturar hasta su revisión (P-07)");
        }

        Liquidacion liq = Liquidador.liquidar(tipo, lectura.getConsumo(), tarifa, factor);
        LineaFactura l = new LineaFactura();
        l.setContratoId(c.getId());
        l.setTipoServicio(tipo);
        l.setTarifaId(tarifa.getId());
        l.setFactorSubsidioId(factor == null ? null : factor.getId());
        l.setLecturaId(lectura.getId());
        l.setConsumo(liq.consumo());
        l.setConsumoBasico(liq.consumoBasico());
        l.setConsumoComplementario(liq.consumoComplementario());
        l.setConsumoSuntuario(liq.consumoSuntuario());
        l.setCargoFijo(liq.cargoFijo());
        l.setValorConsumo(liq.valorConsumo());
        l.setSubsidio(liq.subsidio());
        l.setContribucion(liq.contribucion());
        l.setOtrosCargos(liq.otrosCargos());
        l.setInteresesMora(liq.interesesMora());
        l.setTotalLinea(liq.totalLinea());
        l.setValorAjuste(BigDecimal.ZERO);
        return l;
    }

    /** P-06: el alcantarillado no tiene medidor; se liquida con el del acueducto vigente del predio. */
    private Long medidorDeLectura(ContratoServicio c) {
        if (c.getTipoServicio() != TipoServicio.ALCANTARILLADO) {
            return c.getMedidorId();
        }
        return contratos.findByPredioIdAndTipoServicioAndEstadoIn(c.getPredioId(), TipoServicio.ACUEDUCTO,
                List.of(EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO)).stream()
                .map(ContratoServicio::getMedidorId).filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new NegocioException("SIN_ACUEDUCTO", "El contrato " + c.getNumeroContrato()
                        + " de alcantarillado no tiene acueducto vigente en el predio para liquidar (P-06)"));
    }

    private String serial(Long medidorId) {
        return medidores.findById(medidorId).map(Medidor::getSerial).orElse(String.valueOf(medidorId));
    }

    /** Correo al titular con el número, el total y el vencimiento (FACTURA_EMITIDA o FACTURA_REEXPEDIDA). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notificar(Factura f, EventoNotificacion evento) {
        Usuario u = usuarios.findById(f.getUsuarioId()).orElseThrow();
        String asunto = (evento == EventoNotificacion.FACTURA_REEXPEDIDA ? "Factura reexpedida " : "Su factura ")
                + f.getNumeroFactura();
        String cuerpo = "Hola " + u.getNombreCompleto() + ",\n\n"
                + (evento == EventoNotificacion.FACTURA_REEXPEDIDA
                        ? "Reexpedimos su factura del periodo " + f.getPeriodo() + " con los valores corregidos.\n"
                        : "Ya está disponible su factura de servicios públicos del periodo " + f.getPeriodo() + ".\n")
                + "Número y referencia de pago: " + f.getNumeroFactura() + "\n"
                + "Total a pagar: " + pesos(f.getSaldoPendiente()) + "\n"
                + "Fecha de vencimiento: " + f.getFechaVencimiento() + "\n\n"
                + "Puede consultarla, descargarla y pagarla en línea en el portal de EPQ.\n";
        notificaciones.correo(evento, u.getCorreo(), u.getId(), asunto, cuerpo, "FACTURA", f.getId());
    }

    static String pesos(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(CO).format(v == null ? BigDecimal.ZERO : v);
    }
}
