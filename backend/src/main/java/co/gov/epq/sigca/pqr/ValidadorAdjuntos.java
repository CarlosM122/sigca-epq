package co.gov.epq.sigca.pqr;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;

import co.gov.epq.sigca.common.error.NegocioException;

/**
 * Valida los adjuntos de una PQR por su contenido real y no por lo que declara el navegador:
 * la firma mágica decide el tipo (PDF, JPEG o PNG), la extensión del nombre debe coincidir con él,
 * el tamaño no supera ADJUNTO_MAX_MB y el nombre queda sin rutas ni caracteres que rechaza CK_PQRADJ_NOMBRE.
 * Calcula el SHA-256 que se guarda en HASH_SHA256.
 */
public final class ValidadorAdjuntos {

    public static final int MAX_ARCHIVOS = 5;
    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";

    private static final byte[] FIRMA_PDF = { 0x25, 0x50, 0x44, 0x46, 0x2D };
    private static final byte[] FIRMA_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] FIRMA_PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    private static final int MAX_NOMBRE = 200;

    /** Archivo recibido, antes de validar. */
    public record Entrada(String nombreOriginal, byte[] contenido) {
    }

    /** Archivo listo para guardarse en PQR_ADJUNTO. */
    public record Archivo(String nombre, String tipoMime, byte[] contenido, String sha256) {
    }

    private ValidadorAdjuntos() {
    }

    public static List<Archivo> validar(List<Entrada> entradas, long maxBytes) {
        if (entradas == null || entradas.isEmpty()) {
            return List.of();
        }
        if (entradas.size() > MAX_ARCHIVOS) {
            throw new NegocioException("ADJUNTO_INVALIDO", "Se admiten máximo " + MAX_ARCHIVOS + " archivos por envío");
        }
        List<Archivo> archivos = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (Entrada e : entradas) {
            Archivo a = validar(e, maxBytes);
            if (!hashes.add(a.sha256())) {
                throw new NegocioException("ADJUNTO_DUPLICADO", "El archivo \"" + a.nombre() + "\" está repetido");
            }
            archivos.add(a);
        }
        return archivos;
    }

    public static Archivo validar(Entrada e, long maxBytes) {
        byte[] c = e.contenido();
        String original = e.nombreOriginal() == null ? "" : e.nombreOriginal();
        if (c == null || c.length == 0) {
            throw new NegocioException("ADJUNTO_INVALIDO", "El archivo \"" + original + "\" está vacío");
        }
        if (c.length > maxBytes) {
            throw new NegocioException("ARCHIVO_DEMASIADO_GRANDE", "El archivo \"" + original + "\" supera "
                    + (maxBytes / 1_048_576) + " MB", HttpStatus.CONTENT_TOO_LARGE);
        }
        String tipo = detectarTipo(c);
        if (tipo == null) {
            throw new NegocioException("ADJUNTO_INVALIDO",
                    "El archivo \"" + original + "\" no es un PDF, JPG o PNG válido");
        }
        String extension = extension(original);
        if (extension != null && !extensionCorresponde(extension, tipo)) {
            throw new NegocioException("ADJUNTO_INVALIDO",
                    "El contenido de \"" + original + "\" no corresponde a su extensión ." + extension);
        }
        return new Archivo(nombreSeguro(original, tipo), tipo, c, sha256(c));
    }

    /** Tipo MIME según los primeros bytes del archivo, o null si no es un tipo admitido. */
    public static String detectarTipo(byte[] c) {
        if (empiezaCon(c, FIRMA_PDF)) {
            return PDF;
        }
        if (empiezaCon(c, FIRMA_PNG)) {
            return PNG;
        }
        if (empiezaCon(c, FIRMA_JPEG)) {
            return JPEG;
        }
        return null;
    }

    /** Nombre sin ruta ni caracteres reservados, de máximo 200 caracteres y con la extensión del tipo real. */
    public static String nombreSeguro(String original, String tipo) {
        String base = original == null ? "" : original;
        int corte = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(corte + 1)
                .replaceAll("[\\p{Cntrl}:*?\"<>|]", "_")
                .strip();
        String ext = extension(base);
        if (ext == null || !extensionCorresponde(ext, tipo)) {
            ext = PDF.equals(tipo) ? "pdf" : PNG.equals(tipo) ? "png" : "jpg";
            base = (base.isEmpty() ? "adjunto" : base) + "." + ext;
        }
        if (base.startsWith(".")) {
            base = "adjunto" + base;
        }
        if (base.length() > MAX_NOMBRE) {
            String sufijo = "." + ext;
            base = base.substring(0, MAX_NOMBRE - sufijo.length()) + sufijo;
        }
        return base;
    }

    public static String sha256(byte[] c) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean extensionCorresponde(String ext, String tipo) {
        return switch (ext) {
            case "pdf" -> PDF.equals(tipo);
            case "png" -> PNG.equals(tipo);
            case "jpg", "jpeg" -> JPEG.equals(tipo);
            default -> false;
        };
    }

    private static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 || punto == nombre.length() - 1 ? null : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean empiezaCon(byte[] c, byte[] firma) {
        if (c.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (c[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
