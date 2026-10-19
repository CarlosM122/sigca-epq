package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Respuestas de la API de cartera, cobro persuasivo y acuerdos de pago. */
public final class CarteraVistas {

    private CarteraVistas() {
    }

    /** Usuario con facturas VENCIDAS (P-11). La deuda exigible excluye lo que está en reclamo (RN-33). */
    public record Moroso(Long usuarioId, String codigoUsuario, String nombre, String municipios, int facturasVencidas,
            int periodosMora, BigDecimal saldoVencido, BigDecimal valorEnReclamo, BigDecimal deudaExigible,
            LocalDate primerVencimiento, int diasMora, Long cobroId, EstadoCobro estadoCobro, boolean candidatoSuspension,
            LocalDateTime fechaAvisoSuspension, LocalDate fechaInicioCobro, boolean acuerdoVigente,
            int ordenesSuspensionPendientes, int contratosActivos, boolean puedeSerCandidato) {
    }

    public record FacturaVencida(Long id, String numeroFactura, int periodo, LocalDate fechaVencimiento,
            BigDecimal totalAPagar, BigDecimal saldoPendiente, BigDecimal valorEnReclamo, String uso, String direccion,
            String municipio, boolean incluible) {
    }

    /** Lo necesario para armar un acuerdo: facturas y topes vigentes (P-12, RN-38). */
    public record DeudaUsuario(Long usuarioId, String codigoUsuario, String nombre, List<FacturaVencida> facturas,
            BigDecimal totalIncluible, BigDecimal tasaMaxima, BigDecimal tasaUsura, BigDecimal cuotaInicialPct,
            int minCuotas, int maxCuotas, String acuerdoVigente) {
    }

    public record CuotaPlan(int numeroCuota, LocalDate fechaVencimiento, BigDecimal valorCapital, BigDecimal valorInteres,
            BigDecimal valorCuota, BigDecimal saldoDespues) {
    }

    public record Simulacion(Long usuarioId, List<Long> facturaIds, BigDecimal totalDeuda, BigDecimal cuotaInicial,
            BigDecimal cuotaInicialMinima, BigDecimal tasaInteresAnual, BigDecimal tasaMaxima,
            BigDecimal tasaInteresMensual, int numeroCuotas, BigDecimal saldoFinanciado, BigDecimal cuotaFija,
            BigDecimal totalIntereses, BigDecimal totalAPagar, List<CuotaPlan> cuotas) {
    }

    public record Acuerdo(Long id, String numeroAcuerdo, Long usuarioId, String codigoUsuario, String titular,
            LocalDate fechaAcuerdo, BigDecimal totalDeuda, BigDecimal cuotaInicial, BigDecimal tasaInteresAnual,
            int numeroCuotas, EstadoAcuerdo estado, LocalDateTime fechaEstado, String motivoEstado, String funcionario,
            int cuotasPagadas, int cuotasVencidas, LocalDate proximoVencimiento, BigDecimal saldoPorPagar) {
    }

    public record FacturaAcuerdo(Long facturaId, String numeroFactura, int periodo, BigDecimal valorIncluido,
            String estadoFactura) {
    }

    public record CuotaVista(Long id, int numeroCuota, LocalDate fechaVencimiento, BigDecimal valorCapital,
            BigDecimal valorInteres, BigDecimal valorCuota, EstadoCuota estado, LocalDateTime fechaPago) {
    }

    public record AcuerdoDetalle(Acuerdo acuerdo, List<FacturaAcuerdo> facturas, List<CuotaVista> cuotas) {
    }

    public record Cobro(Long id, Long usuarioId, EstadoCobro estado, LocalDate fechaInicio, BigDecimal valorDeuda,
            int periodosMora, boolean candidatoSuspension, LocalDateTime fechaAvisoSuspension, Long acuerdoId,
            String observacion, List<Orden> ordenes) {
    }

    public record Orden(Long id, String numeroOrden, Long contratoId, String numeroContrato, String estado,
            LocalDateTime fechaOrden) {
    }
}
