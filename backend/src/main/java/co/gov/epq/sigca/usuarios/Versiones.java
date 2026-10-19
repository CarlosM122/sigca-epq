package co.gov.epq.sigca.usuarios;

import java.util.Objects;

import co.gov.epq.sigca.common.error.ConflictoException;

/** Bloqueo optimista explícito: el cliente envía la versión que leyó y se compara antes de modificar. */
public final class Versiones {

    private Versiones() {
    }

    public static void exigir(Long enviada, Long actual) {
        if (enviada != null && !Objects.equals(enviada, actual)) {
            throw new ConflictoException("MODIFICACION_CONCURRENTE",
                    "El registro fue modificado por otro usuario. Recargue e intente de nuevo");
        }
    }
}
