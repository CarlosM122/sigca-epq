package co.gov.epq.sigca.notificaciones;

/**
 * Puerto del operador de SMS. Debe lanzar excepción si el envío falla para que se reintente.
 */
public interface EnviadorSms {

    void enviar(String telefono, String texto);
}
