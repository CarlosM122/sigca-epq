package co.gov.epq.sigca.pqr;

import java.time.LocalDateTime;

/** Datos de un adjunto sin su contenido. */
public record PqrAdjuntoMetadatos(Long id, TipoAdjunto tipo, String nombreArchivo, String tipoMime, Long tamanoBytes,
        LocalDateTime cargadoEn) {
}
