package co.gov.epq.sigca.pagos;

/**
 * Puerto hacia la pasarela (P-18, IS-02). El flujo es por redirección: SIGCA crea el intento y envía al ciudadano
 * a la página de la pasarela; nunca recibe datos de tarjeta. La confirmación llega por el webhook firmado.
 */
public interface PasarelaPagos {

    /** URL a la que se redirige al ciudadano para pagar la transacción. */
    String urlCheckout(TransaccionPasarela transaccion);
}
