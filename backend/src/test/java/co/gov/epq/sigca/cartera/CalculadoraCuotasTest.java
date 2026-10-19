package co.gov.epq.sigca.cartera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculadoraCuotasTest {

    private static final LocalDate HOY = LocalDate.of(2026, 1, 31);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void tasaMensualEquivalenteALaEfectivaAnual() {
        // (1,06)^(1/12) - 1 = 0,004867550565...
        assertThat(CalculadoraCuotas.tasaMensual(bd("6"))).isEqualByComparingTo("0.0048675506");
        assertThat(CalculadoraCuotas.tasaMensual(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void cuotaInicialMinimaRedondeaComoElTrigger() {
        assertThat(CalculadoraCuotas.cuotaInicialMinima(bd("147000"), bd("10"))).isEqualByComparingTo("14700.00");
        assertThat(CalculadoraCuotas.cuotaInicialMinima(bd("100.05"), bd("10"))).isEqualByComparingTo("10.01");
    }

    @Test
    void planFrancesConCuotaInicialSinInteres() {
        CalculadoraCuotas.Plan p = CalculadoraCuotas.calcular(bd("147000"), bd("14700"), bd("6"), 6, HOY);

        assertThat(p.cuotas()).hasSize(7);
        CalculadoraCuotas.Cuota c0 = p.cuotas().get(0);
        assertThat(c0.numero()).isZero();
        assertThat(c0.capital()).isEqualByComparingTo("14700");
        assertThat(c0.interes()).isEqualByComparingTo("0");
        assertThat(c0.fechaVencimiento()).isEqualTo(HOY.plusDays(5));

        // Saldo 132.300 a 6 cuotas con i = 0,0048675506: A = 22.427,17
        assertThat(p.cuotaFija()).isEqualByComparingTo("22427.17");
        CalculadoraCuotas.Cuota c1 = p.cuotas().get(1);
        assertThat(c1.interes()).isEqualByComparingTo("643.98"); // 132.300 × i
        assertThat(c1.capital()).isEqualByComparingTo("21783.19");
        assertThat(c1.valor()).isEqualByComparingTo("22427.17");
        // Fin de mes: 31 ene + 1 mes = 28 feb; las fechas son distintas (UK_CUOTA_VENCIMIENTO)
        assertThat(c1.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(p.cuotas()).extracting(CalculadoraCuotas.Cuota::fechaVencimiento).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @CsvSource({ "147000, 14700, 6, 6", "147000, 14700, 0, 7", "1000000, 100000, 25, 24", "999.99, 100, 6, 2",
            "250000.55, 25000.06, 5.5, 13", "10, 1, 25, 24" })
    void laSumaDelCapitalEsExactamenteLaDeuda(String deuda, String inicial, String tasa, int n) {
        CalculadoraCuotas.Plan p = CalculadoraCuotas.calcular(bd(deuda), bd(inicial), bd(tasa), n, HOY);
        BigDecimal capital = p.cuotas().stream().map(CalculadoraCuotas.Cuota::capital).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(capital).isEqualByComparingTo(deuda);
        assertThat(p.cuotas()).hasSize(n + 1);
        assertThat(p.cuotas()).allSatisfy(c -> {
            assertThat(c.capital().signum()).isPositive();
            assertThat(c.interes().signum()).isNotNegative();
            assertThat(c.valor()).isEqualByComparingTo(c.capital().add(c.interes()));
            assertThat(c.capital().scale()).isLessThanOrEqualTo(2);
        });
        assertThat(p.cuotas().getLast().saldoDespues()).isEqualByComparingTo("0");
        // Todas las cuotas mensuales salvo la última valen la cuota fija; la última solo difiere en centavos
        for (int k = 1; k < n; k++) {
            assertThat(p.cuotas().get(k).valor()).isEqualByComparingTo(p.cuotaFija());
        }
        assertThat(p.cuotas().getLast().valor().subtract(p.cuotaFija()).abs()).isLessThan(bd("1.00"));
        assertThat(p.totalAPagar()).isEqualByComparingTo(p.totalDeuda().add(p.totalIntereses()));
    }

    @Test
    void sinInteresLasCuotasSonIguales() {
        CalculadoraCuotas.Plan p = CalculadoraCuotas.calcular(bd("100"), bd("10"), BigDecimal.ZERO, 7, HOY);
        assertThat(p.totalIntereses()).isEqualByComparingTo("0");
        assertThat(p.cuotaFija()).isEqualByComparingTo("12.86");
        assertThat(p.cuotas().getLast().capital()).isEqualByComparingTo("12.84"); // 90 - 6 × 12,86
    }

    @Test
    void rechazaDatosInvalidos() {
        assertThatThrownBy(() -> CalculadoraCuotas.calcular(bd("100"), bd("100"), bd("6"), 2, HOY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCuotas.calcular(bd("100"), bd("0"), bd("6"), 2, HOY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCuotas.calcular(bd("1.05"), bd("1"), bd("6"), 6, HOY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCuotas.calcular(bd("100"), bd("10"), bd("-1"), 2, HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
