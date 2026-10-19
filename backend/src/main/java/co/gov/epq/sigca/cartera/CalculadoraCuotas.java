package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan de pagos de un acuerdo (P-12). Cálculo puro, sin dependencias de Spring ni de la BD:
 * <ul>
 * <li>Cuota 0: la cuota inicial, solo capital y sin intereses; vence {@link #DIAS_CUOTA_INICIAL} días después del acuerdo.</li>
 * <li>Cuotas 1..n: amortización francesa del saldo (deuda − cuota inicial) con la tasa mensual equivalente a la
 * efectiva anual: {@code i = (1 + TEA)^(1/12) − 1}, redondeada a 10 decimales. La cuota k vence k meses después del
 * acuerdo.</li>
 * <li>Intereses y cuota fija se redondean HALF_UP a 2 decimales; la última cuota toma todo el capital restante, así la
 * suma del capital de todas las cuotas (incluida la 0) es exactamente la deuda total (CK_ACUERDO_CUADRE).</li>
 * </ul>
 */
public final class CalculadoraCuotas {

    public static final int DIAS_CUOTA_INICIAL = 5;
    static final BigDecimal CIEN = BigDecimal.valueOf(100);
    static final BigDecimal CENTAVO = new BigDecimal("0.01");

    private CalculadoraCuotas() {
    }

    public record Cuota(int numero, LocalDate fechaVencimiento, BigDecimal capital, BigDecimal interes, BigDecimal valor,
            BigDecimal saldoDespues) {
    }

    public record Plan(BigDecimal totalDeuda, BigDecimal cuotaInicial, BigDecimal saldoFinanciado,
            BigDecimal tasaAnual, BigDecimal tasaMensual, int numeroCuotas, BigDecimal cuotaFija,
            BigDecimal totalIntereses, BigDecimal totalAPagar, List<Cuota> cuotas) {
    }

    /** Tasa mensual equivalente a una efectiva anual en porcentaje (6 → 0.0048675506). */
    public static BigDecimal tasaMensual(BigDecimal tasaAnualPct) {
        if (tasaAnualPct.signum() == 0) {
            return BigDecimal.ZERO.setScale(10);
        }
        double tea = tasaAnualPct.divide(CIEN, MathContext.DECIMAL64).doubleValue();
        return BigDecimal.valueOf(StrictMath.pow(1 + tea, 1.0 / 12) - 1).setScale(10, RoundingMode.HALF_UP);
    }

    /** Cuota inicial mínima: ROUND(deuda × pct / 100, 2), igual que el trigger. */
    public static BigDecimal cuotaInicialMinima(BigDecimal totalDeuda, BigDecimal pct) {
        return totalDeuda.multiply(pct).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    /**
     * @throws IllegalArgumentException si los datos no permiten un plan válido (deuda o cuota inicial no positivas,
     *         cuota inicial ≥ deuda, menos de una cuota, tasa negativa o saldo menor a un centavo por cuota).
     */
    public static Plan calcular(BigDecimal totalDeuda, BigDecimal cuotaInicial, BigDecimal tasaAnualPct, int numeroCuotas,
            LocalDate fechaAcuerdo) {
        BigDecimal deuda = totalDeuda.setScale(2, RoundingMode.HALF_UP);
        BigDecimal inicial = cuotaInicial.setScale(2, RoundingMode.HALF_UP);
        if (deuda.signum() <= 0 || inicial.signum() <= 0 || inicial.compareTo(deuda) >= 0) {
            throw new IllegalArgumentException("La cuota inicial debe ser positiva y menor que la deuda");
        }
        if (numeroCuotas < 1) {
            throw new IllegalArgumentException("Debe haber al menos una cuota");
        }
        if (tasaAnualPct.signum() < 0) {
            throw new IllegalArgumentException("La tasa no puede ser negativa");
        }
        BigDecimal financiado = deuda.subtract(inicial);
        if (financiado.compareTo(CENTAVO.multiply(BigDecimal.valueOf(numeroCuotas))) < 0) {
            throw new IllegalArgumentException("El saldo a financiar es muy pequeño para ese número de cuotas");
        }
        BigDecimal i = tasaMensual(tasaAnualPct);
        BigDecimal fija = cuotaFija(financiado, i, numeroCuotas);

        List<Cuota> cuotas = new ArrayList<>();
        cuotas.add(new Cuota(0, fechaAcuerdo.plusDays(DIAS_CUOTA_INICIAL), inicial, BigDecimal.ZERO.setScale(2), inicial,
                financiado));
        BigDecimal saldo = financiado;
        BigDecimal intereses = BigDecimal.ZERO.setScale(2);
        for (int k = 1; k <= numeroCuotas; k++) {
            BigDecimal interes = saldo.multiply(i).setScale(2, RoundingMode.HALF_UP);
            BigDecimal capital = k == numeroCuotas ? saldo : fija.subtract(interes);
            if (capital.signum() <= 0 || capital.compareTo(saldo) > 0) {
                // Tasa tan alta o saldo tan pequeño que la cuota no amortiza: se reparte el saldo restante.
                capital = saldo.divide(BigDecimal.valueOf(numeroCuotas - k + 1L), 2, RoundingMode.HALF_UP).max(CENTAVO);
            }
            saldo = saldo.subtract(capital);
            intereses = intereses.add(interes);
            cuotas.add(new Cuota(k, fechaAcuerdo.plusMonths(k), capital, interes, capital.add(interes), saldo));
        }
        return new Plan(deuda, inicial, financiado, tasaAnualPct, i, numeroCuotas, fija, intereses,
                deuda.add(intereses), List.copyOf(cuotas));
    }

    /** Cuota francesa A = P·i / (1 − (1 + i)^−n), o P / n sin interés; HALF_UP a 2 decimales. */
    static BigDecimal cuotaFija(BigDecimal p, BigDecimal i, int n) {
        if (i.signum() == 0) {
            return p.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        }
        MathContext mc = MathContext.DECIMAL128;
        BigDecimal factor = BigDecimal.ONE.add(i).pow(n, mc);
        BigDecimal descuento = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(factor, mc));
        return p.multiply(i, mc).divide(descuento, mc).setScale(2, RoundingMode.HALF_UP);
    }
}
