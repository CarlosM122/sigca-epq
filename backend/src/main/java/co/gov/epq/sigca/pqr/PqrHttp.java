package co.gov.epq.sigca.pqr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/** Conversión de partes multipart a entradas del validador y respuesta segura de descarga de adjuntos. */
final class PqrHttp {

    private PqrHttp() {
    }

    static List<ValidadorAdjuntos.Entrada> entradas(List<MultipartFile> archivos) {
        List<ValidadorAdjuntos.Entrada> lista = new ArrayList<>();
        if (archivos == null) {
            return lista;
        }
        for (MultipartFile f : archivos) {
            if (f == null || f.isEmpty() && (f.getOriginalFilename() == null || f.getOriginalFilename().isBlank())) {
                continue;
            }
            try {
                lista.add(new ValidadorAdjuntos.Entrada(f.getOriginalFilename(), f.getBytes()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return lista;
    }

    static ResponseEntity<byte[]> descarga(PqrAdjunto a) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.getNombreArchivo(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(a.getContenido());
    }
}
