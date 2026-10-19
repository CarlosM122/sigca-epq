package co.gov.epq.sigca.facturacion;

import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** ERP simulado (P-19): acepta todo y devuelve una referencia sintética. */
@Component
@ConditionalOnProperty(name = "sigca.integraciones.erp.modo", havingValue = "simulado", matchIfMissing = true)
public class ErpSimulado implements ErpFinanciero {

    private static final Logger log = LoggerFactory.getLogger(ErpSimulado.class);

    @Override
    public String registrarFactura(FacturaErp factura) {
        log.debug("ERP simulado: factura {}", factura.numeroFactura());
        return "ERP-F-" + sufijo();
    }

    @Override
    public String registrarPago(PagoErp pago) {
        log.debug("ERP simulado: pago {}", pago.codigoPago());
        return "ERP-P-" + sufijo();
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }
}
