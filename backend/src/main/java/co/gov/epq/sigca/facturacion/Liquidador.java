package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.tarifas.FactorSubsidio;
import co.gov.epq.sigca.tarifas.Tarifa;

/**
 * Liquidación de una línea de factura (P-08, RN-37). Replica paso a paso TRG_LINEA_BI: la BD recalcula
 * y rechaza cualquier diferencia (-20018), así que este cálculo debe dar exactamente lo mismo.
 * <ul>
 * <li>GAS: todo el consumo es básico, sin complementario, suntuario, subsidio ni contribución.</li>
 * <li>Otros: básico = min(consumo, CONSUMO_BASICO_M3); suntuario = max(consumo − CONSUMO_SUNTUARIO_DESDE_M3, 0);
 * complementario = el resto.</li>
 * <li>Valor del consumo = ROUND(b·VB + c·VC + s·VS, 2).</li>
 * <li>Factor &lt; 0: subsidio = ROUND(b·VB·%/100, 2), solo sobre el básico. Factor &gt; 0: contribución =
 * ROUND((cargo fijo + valor)·%/100, 2).</li>
 * </ul>
 * Oracle redondea a la mitad alejándose de cero, igual que {@link RoundingMode#HALF_UP}.
 */
public final class Liquidador {

    private Liquidador() {
    }

    /** Resultado de liquidar un consumo con una tarifa y un factor. */
    public record Liquidacion(BigDecimal consumo, BigDecimal consumoBasico, BigDecimal consumoComplementario,
            BigDecimal consumoSuntuario, BigDecimal cargoFijo, BigDecimal valorConsumo, BigDecimal subsidio,
            BigDecimal contribucion, BigDecimal otrosCargos, BigDecimal interesesMora, BigDecimal totalLinea) {
    }

    public static Liquidacion liquidar(TipoServicio tipo, BigDecimal consumo, Tarifa tarifa, FactorSubsidio factor) {
        return liquidar(tipo, consumo, tarifa, factor, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public static Liquidacion liquidar(TipoServicio tipo, BigDecimal consumo, Tarifa tarifa, FactorSubsidio factor,
            BigDecimal otrosCargos, BigDecimal interesesMora) {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(consumo, "consumo");
        Objects.requireNonNull(tarifa, "tarifa");
        if (consumo.signum() < 0) {
            throw new IllegalArgumentException("El consumo no puede ser negativo");
        }
        if (tarifa.getTipoServicio() != tipo) {
            throw new IllegalArgumentException("La tarifa no es del servicio " + tipo);
        }
        boolean gas = tipo == TipoServicio.GAS;
        if (!gas && factor == null) {
            throw new IllegalArgumentException("Acueducto y alcantarillado requieren factor de subsidio o contribución");
        }

        BigDecimal b;
        BigDecimal c;
        BigDecimal s;
        if (gas) {
            b = consumo;
            c = BigDecimal.ZERO;
            s = BigDecimal.ZERO;
        } else {
            b = consumo.min(tarifa.getConsumoBasicoM3());
            s = consumo.subtract(tarifa.getConsumoSuntuarioDesdeM3()).max(BigDecimal.ZERO);
            c = consumo.subtract(b).subtract(s);
        }

        BigDecimal valorBasico = b.multiply(tarifa.getValorM3Basico());
        BigDecimal valor = valorBasico
                .add(c.multiply(nvl(tarifa.getValorM3Complementario())))
                .add(s.multiply(nvl(tarifa.getValorM3Suntuario())));
        valor = redondear(valor);

        BigDecimal subsidio = BigDecimal.ZERO;
        BigDecimal contribucion = BigDecimal.ZERO;
        if (!gas) {
            BigDecimal pct = factor.getPorcentaje();
            if (pct.signum() < 0) {
                subsidio = redondear(valorBasico.multiply(pct).movePointLeft(2));
            } else if (pct.signum() > 0) {
                contribucion = redondear(tarifa.getCargoFijo().add(valor).multiply(pct).movePointLeft(2));
            }
        }

        BigDecimal otros = nvl(otrosCargos);
        BigDecimal intereses = nvl(interesesMora);
        BigDecimal total = tarifa.getCargoFijo().add(valor).add(subsidio).add(contribucion).add(otros).add(intereses);
        return new Liquidacion(consumo, b, c, s, tarifa.getCargoFijo(), valor, subsidio.setScale(2),
                contribucion.setScale(2), otros, intereses, total);
    }

    private static BigDecimal redondear(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
