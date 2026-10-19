package co.gov.epq.sigca.pqr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.gov.epq.sigca.common.error.NegocioException;

class ValidadorAdjuntosTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
    static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };
    static final long MB = 1_048_576;

    private static ValidadorAdjuntos.Entrada e(String nombre, byte[] c) {
        return new ValidadorAdjuntos.Entrada(nombre, c);
    }

    private static String codigo(Runnable r) {
        try {
            r.run();
        } catch (NegocioException ex) {
            return ex.getCodigo();
        }
        return null;
    }

    @Test
    void detectaElTipoPorLosBytesYNoPorElNombre() {
        assertThat(ValidadorAdjuntos.detectarTipo(PDF)).isEqualTo(ValidadorAdjuntos.PDF);
        assertThat(ValidadorAdjuntos.detectarTipo(PNG)).isEqualTo(ValidadorAdjuntos.PNG);
        assertThat(ValidadorAdjuntos.detectarTipo(JPG)).isEqualTo(ValidadorAdjuntos.JPEG);
        assertThat(ValidadorAdjuntos.detectarTipo("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(ValidadorAdjuntos.detectarTipo(new byte[] { 0x25 })).isNull();
    }

    @Test
    void aceptaPdfPngYJpgYCalculaSha256() {
        ValidadorAdjuntos.Archivo a = ValidadorAdjuntos.validar(e("factura.PDF", PDF), MB);
        assertThat(a.tipoMime()).isEqualTo("application/pdf");
        assertThat(a.nombre()).isEqualTo("factura.PDF");
        assertThat(a.sha256()).hasSize(64).isEqualTo(ValidadorAdjuntos.sha256(PDF));
        assertThat(ValidadorAdjuntos.validar(e("foto.jpeg", JPG), MB).tipoMime()).isEqualTo("image/jpeg");
        assertThat(ValidadorAdjuntos.validar(e("captura.png", PNG), MB).tipoMime()).isEqualTo("image/png");
    }

    @Test
    void rechazaContenidoQueNoCorrespondeALaExtension() {
        assertThat(codigo(() -> ValidadorAdjuntos.validar(e("virus.exe", PDF), MB))).isEqualTo("ADJUNTO_INVALIDO");
        assertThat(codigo(() -> ValidadorAdjuntos.validar(e("foto.png", JPG), MB))).isEqualTo("ADJUNTO_INVALIDO");
        assertThat(codigo(() -> ValidadorAdjuntos.validar(e("doc.pdf", "<html>".getBytes(StandardCharsets.UTF_8)), MB)))
                .isEqualTo("ADJUNTO_INVALIDO");
        assertThat(codigo(() -> ValidadorAdjuntos.validar(e("vacio.pdf", new byte[0]), MB))).isEqualTo("ADJUNTO_INVALIDO");
    }

    @Test
    void rechazaArchivosGrandesConEstado413() {
        byte[] grande = Arrays.copyOf(PDF, (int) MB + 1);
        assertThatThrownBy(() -> ValidadorAdjuntos.validar(e("grande.pdf", grande), MB))
                .isInstanceOfSatisfying(NegocioException.class, ex -> {
                    assertThat(ex.getCodigo()).isEqualTo("ARCHIVO_DEMASIADO_GRANDE");
                    assertThat(ex.getStatus().value()).isEqualTo(413);
                });
    }

    @Test
    void rechazaDuplicadosYMasDeCincoArchivos() {
        assertThat(codigo(() -> ValidadorAdjuntos.validar(List.of(e("a.pdf", PDF), e("b.pdf", PDF)), MB)))
                .isEqualTo("ADJUNTO_DUPLICADO");
        List<ValidadorAdjuntos.Entrada> seis = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> e(i + ".pdf", (new String(PDF, StandardCharsets.US_ASCII) + i).getBytes(StandardCharsets.US_ASCII)))
                .toList();
        assertThat(codigo(() -> ValidadorAdjuntos.validar(seis, MB))).isEqualTo("ADJUNTO_INVALIDO");
        assertThat(ValidadorAdjuntos.validar(List.of(), MB)).isEmpty();
    }

    @Test
    void elNombreSeguroQuitaRutasYCaracteresReservados() {
        assertThat(ValidadorAdjuntos.nombreSeguro("../../etc/passwd", ValidadorAdjuntos.PDF)).isEqualTo("passwd.pdf");
        assertThat(ValidadorAdjuntos.nombreSeguro("C:\\Users\\x\\re:cibo?.pdf", ValidadorAdjuntos.PDF)).isEqualTo("re_cibo_.pdf");
        assertThat(ValidadorAdjuntos.nombreSeguro("", ValidadorAdjuntos.PNG)).isEqualTo("adjunto.png");
        assertThat(ValidadorAdjuntos.nombreSeguro(".pdf", ValidadorAdjuntos.PDF)).isEqualTo("adjunto.pdf");
        assertThat(ValidadorAdjuntos.nombreSeguro("a\u0000b\r\n.jpg", ValidadorAdjuntos.JPEG)).isEqualTo("a_b__.jpg");
        String largo = "x".repeat(300) + ".pdf";
        assertThat(ValidadorAdjuntos.nombreSeguro(largo, ValidadorAdjuntos.PDF)).hasSize(200).endsWith(".pdf");
    }
}
