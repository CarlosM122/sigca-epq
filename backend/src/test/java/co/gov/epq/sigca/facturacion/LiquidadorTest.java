package co.gov.epq.sigca.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.facturacion.Liquidador.Liquidacion;
import co.gov.epq.sigca.tarifas.FactorSubsidio;
import co.gov.epq.sigca.tarifas.Tarifa;

/**
 * Casos de 05_pruebas.sql / 05_pruebas_resultado.txt (tarifas de Circasia y Salento) y bordes del redondeo.
 */
class LiquidadorTest {

    private static Tarifa tarifaCra(TipoServicio tipo, String cargoFijo, String vb, String vc, String vs) {
        Tarifa t = new Tarifa();
        t.setTipoServicio(tipo);
        t.setCargoFijo(new BigDecimal(cargoFijo));
        t.setValorM3Basico(new BigDecimal(vb));
        t.setValorM3Complementario(new BigDecimal(vc));
        t.setValorM3Suntuario(new BigDecimal(vs));
        t.setConsumoBasicoM3(new BigDecimal("13"));
        t.setConsumoSuntuarioDesdeM3(new BigDecimal("26"));
        return t;
    }

    private static Tarifa tarifaGas(String cargoFijo, String vb) {
        Tarifa t = new Tarifa();
        t.setTipoServicio(TipoServicio.GAS);
        t.setCargoFijo(new BigDecimal(cargoFijo));
        t.setValorM3Basico(new BigDecimal(vb));
        return t;
    }

    private static FactorSubsidio factor(String porcentaje) {
        FactorSubsidio f = new FactorSubsidio();
        f.setPorcentaje(new BigDecimal(porcentaje));
        return f;
    }

    private static final Tarifa ACUEDUCTO_CIRCASIA = tarifaCra(TipoServicio.ACUEDUCTO, "12000", "2500", "2500", "3000");
    private static final Tarifa ALCANTARILLADO_CIRCASIA = tarifaCra(TipoServicio.ALCANTARILLADO, "8000", "1900", "1900", "2300");
    private static final Tarifa GAS_CIRCASIA = tarifaGas("9000", "3100");
    private static final Tarifa ACUEDUCTO_SALENTO = tarifaCra(TipoServicio.ACUEDUCTO, "11000", "2400", "2400", "2900");

    @Test
    void acueductoEstrato2Con37m3RepartidoEnLosTresRangosYSubsidioSoloDelBasico() {
        Liquidacion l = Liquidador.liquidar(TipoServicio.ACUEDUCTO, new BigDecimal("37"), ACUEDUCTO_CIRCASIA, factor("-40"));

        assertThat(l.consumoBasico()).isEqualByComparingTo("13");
        assertThat(l.consumoComplementario()).isEqualByComparingTo("13");
        assertThat(l.consumoSuntuario()).isEqualByComparingTo("11");
        assertThat(l.valorConsumo()).isEqualByComparingTo("98000");
        assertThat(l.subsidio()).isEqualByComparingTo("-13000");
        assertThat(l.contribucion()).isEqualByComparingTo("0");
        assertThat(l.totalLinea()).isEqualByComparingTo("97000");
    }

    @Test
    void facturaP4De209820() {
        BigDecimal consumo = new BigDecimal("37");
        Liquidacion acu = Liquidador.liquidar(TipoServicio.ACUEDUCTO, consumo, ACUEDUCTO_CIRCASIA, factor("-40"));
        Liquidacion alc = Liquidador.liquidar(TipoServicio.ALCANTARILLADO, consumo, ALCANTARILLADO_CIRCASIA, factor("-40"));
        Liquidacion gas = Liquidador.liquidar(TipoServicio.GAS, new BigDecimal("10"), GAS_CIRCASIA, null);

        assertThat(alc.totalLinea()).isEqualByComparingTo("72820");
        assertThat(gas.totalLinea()).isEqualByComparingTo("40000");
        assertThat(acu.totalLinea().add(alc.totalLinea()).add(gas.totalLinea())).isEqualByComparingTo("209820");
    }

    @Test
    void facturaP5PorPromedioDe147120() {
        BigDecimal consumo = new BigDecimal("25");
        Liquidacion acu = Liquidador.liquidar(TipoServicio.ACUEDUCTO, consumo, ACUEDUCTO_CIRCASIA, factor("-40"));
        Liquidacion alc = Liquidador.liquidar(TipoServicio.ALCANTARILLADO, consumo, ALCANTARILLADO_CIRCASIA, factor("-40"));
        Liquidacion gas = Liquidador.liquidar(TipoServicio.GAS, new BigDecimal("10"), GAS_CIRCASIA, null);

        assertThat(acu.consumoSuntuario()).isEqualByComparingTo("0");
        assertThat(acu.consumoComplementario()).isEqualByComparingTo("12");
        assertThat(acu.totalLinea().add(alc.totalLinea()).add(gas.totalLinea())).isEqualByComparingTo("147120");
    }

    @Test
    void facturaP6De172720() {
        BigDecimal consumo = new BigDecimal("30");
        Liquidacion acu = Liquidador.liquidar(TipoServicio.ACUEDUCTO, consumo, ACUEDUCTO_CIRCASIA, factor("-40"));
        Liquidacion alc = Liquidador.liquidar(TipoServicio.ALCANTARILLADO, consumo, ALCANTARILLADO_CIRCASIA, factor("-40"));
        Liquidacion gas = Liquidador.liquidar(TipoServicio.GAS, new BigDecimal("10"), GAS_CIRCASIA, null);

        assertThat(acu.totalLinea()).isEqualByComparingTo("76000");
        assertThat(acu.totalLinea().add(alc.totalLinea()).add(gas.totalLinea())).isEqualByComparingTo("172720");
    }

    @Test
    void estrato5PagaContribucionDel50SobreCargoFijoYConsumo() {
        Liquidacion l = Liquidador.liquidar(TipoServicio.ACUEDUCTO, new BigDecimal("15"), ACUEDUCTO_SALENTO, factor("50"));

        assertThat(l.valorConsumo()).isEqualByComparingTo("36000");
        assertThat(l.subsidio()).isEqualByComparingTo("0");
        assertThat(l.contribucion()).isEqualByComparingTo("23500");
        assertThat(l.totalLinea()).isEqualByComparingTo("70500");
    }

    @Test
    void gasNoTieneRangosNiFactor() {
        Liquidacion l = Liquidador.liquidar(TipoServicio.GAS, new BigDecimal("41.5"), GAS_CIRCASIA, factor("-70"));

        assertThat(l.consumoBasico()).isEqualByComparingTo("41.5");
        assertThat(l.consumoComplementario()).isZero();
        assertThat(l.consumoSuntuario()).isZero();
        assertThat(l.subsidio()).isZero();
        assertThat(l.contribucion()).isZero();
        assertThat(l.totalLinea()).isEqualByComparingTo("137650");
    }

    @Test
    void factorCeroNoGeneraSubsidioNiContribucion() {
        Liquidacion l = Liquidador.liquidar(TipoServicio.ACUEDUCTO, new BigDecimal("8"), ACUEDUCTO_CIRCASIA, factor("0"));

        assertThat(l.consumoBasico()).isEqualByComparingTo("8");
        assertThat(l.subsidio()).isZero();
        assertThat(l.contribucion()).isZero();
        assertThat(l.totalLinea()).isEqualByComparingTo("32000");
    }

    @Test
    void consumoCero() {
        Liquidacion l = Liquidador.liquidar(TipoServicio.ACUEDUCTO, BigDecimal.ZERO, ACUEDUCTO_CIRCASIA, factor("-70"));

        assertThat(l.valorConsumo()).isZero();
        assertThat(l.subsidio()).isZero();
        assertThat(l.totalLinea()).isEqualByComparingTo("12000");
    }

    @Test
    void redondeaMitadAlejandoseDeCeroComoOracle() {
        Tarifa t = tarifaCra(TipoServicio.ACUEDUCTO, "10000.00", "1234.5678", "1500.1234", "2000.0001");
        // b = 12.345 · 1234.5678 = 15240.739491 ; subsidio −15 % = −2286.11092365
        Liquidacion l = Liquidador.liquidar(TipoServicio.ACUEDUCTO, new BigDecimal("12.345"), t, factor("-15"));
        assertThat(l.valorConsumo()).isEqualByComparingTo("15240.74");
        assertThat(l.subsidio()).isEqualByComparingTo("-2286.11");

        // Contribución con mitad exacta: (10000 + 0.10) · 5 % = 500.005 -> 500.01
        Tarifa t2 = tarifaCra(TipoServicio.ACUEDUCTO, "10000.00", "0.1", "0.1", "0.1");
        Liquidacion l2 = Liquidador.liquidar(TipoServicio.ACUEDUCTO, BigDecimal.ONE, t2, factor("5"));
        assertThat(l2.contribucion()).isEqualByComparingTo("500.01");

        // Subsidio con mitad exacta negativa: 1 · 0.1 · −5 % = −0.005 -> −0.01
        Liquidacion l3 = Liquidador.liquidar(TipoServicio.ACUEDUCTO, BigDecimal.ONE, t2, factor("-5"));
        assertThat(l3.subsidio()).isEqualByComparingTo("-0.01");
    }

    @Test
    void consumoEnElLimiteSuntuario() {
        Liquidacion l = Liquidador.liquidar(TipoServicio.ALCANTARILLADO, new BigDecimal("26"), ALCANTARILLADO_CIRCASIA, factor("-15"));
        assertThat(l.consumoBasico()).isEqualByComparingTo("13");
        assertThat(l.consumoComplementario()).isEqualByComparingTo("13");
        assertThat(l.consumoSuntuario()).isEqualByComparingTo("0");
    }

    @Test
    void acueductoSinFactorEsUnError() {
        assertThatThrownBy(() -> Liquidador.liquidar(TipoServicio.ACUEDUCTO, BigDecimal.TEN, ACUEDUCTO_CIRCASIA, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Liquidador.liquidar(TipoServicio.GAS, BigDecimal.TEN, ACUEDUCTO_CIRCASIA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
