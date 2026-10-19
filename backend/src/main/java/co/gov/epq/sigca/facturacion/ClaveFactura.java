package co.gov.epq.sigca.facturacion;

/** Una factura es de un titular y un predio en un periodo (P-05). */
public record ClaveFactura(Long usuarioId, Long predioId) {
}
