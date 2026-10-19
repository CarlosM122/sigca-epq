package co.gov.epq.sigca.notificaciones;

/**
 * Puerto de envío de correo (SMTP en producción). Debe lanzar excepción si el envío falla para que se reintente.
 */
public interface EnviadorCorreo {

    void enviar(String destinatario, String asunto, String cuerpo);
}
