package co.gov.epq.sigca.seguridad;

import java.time.LocalDateTime;

/**
 * Regla de intentos fallidos (RN-25): al llegar a {@code PORTAL_MAX_INTENTOS} la cuenta queda bloqueada
 * {@code PORTAL_MINUTOS_BLOQUEO} minutos y el contador vuelve a cero. Aplica a funcionarios y al portal.
 */
public final class ControlIntentos {

    private ControlIntentos() {
    }

    public static boolean bloqueada(LocalDateTime bloqueadaHasta, LocalDateTime ahora) {
        return bloqueadaHasta != null && bloqueadaHasta.isAfter(ahora);
    }

    public static Resultado fallo(Integer intentosActuales, int maximo, int minutosBloqueo, LocalDateTime ahora) {
        int intentos = (intentosActuales == null ? 0 : intentosActuales) + 1;
        if (intentos >= Math.max(1, maximo)) {
            return new Resultado(0, ahora.plusMinutes(minutosBloqueo), true);
        }
        return new Resultado(intentos, null, false);
    }

    /**
     * @param bloqueoNuevo true cuando este fallo es el que bloquea la cuenta (se audita y se notifica una vez)
     */
    public record Resultado(int intentos, LocalDateTime bloqueadaHasta, boolean bloqueoNuevo) {
    }
}
