package co.gov.epq.sigca.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.facturacion.DocumentoPdf;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Pruebas de caracterización de la admisión del pago (taller de refactorización, hallazgos H-04 y H-06).
 * Fijan, antes de la segunda refactorización, qué se rechaza, con qué código, estado HTTP y mensaje, y en qué orden:
 * la regla «la obligación admite pago» en caja y en el pago en línea, la exigencia del valor exacto (P-05) y la
 * validación de la referencia de pago del DTO. No necesitan Oracle ni el contexto de Spring.
 * Regla del taller: después de refactorizar solo pueden cambiar los métodos marcados como fixture.
 */
class AdmisionPagoCaracterizacionTest {

    private static final BigDecimal EXIGIBLE = new BigDecimal("123620.00");
    private static final Long TITULAR = 900L;
    private static final String MOTIVO = "La factura ya está pagada";
    private static final String MENSAJE_REFERENCIA = "Referencia inválida (FAC-AAAAMM-NNNNNNN o AP-AAAA-NNNNNN[-NN])";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    // ------------------------------------------------------------------ admisión en caja y banco

    @Test
    void unaObligacionQueNoAdmitePagoSeRechazaConConflictoYSuMotivo() {
        assertThatThrownBy(() -> exigirPagable(obligacion(false, TITULAR), EXIGIBLE))
                .isExactlyInstanceOf(ConflictoException.class)
                .hasMessage(MOTIVO)
                .satisfies(e -> {
                    assertThat(((NegocioException) e).getCodigo()).isEqualTo("NO_ADMITE_PAGOS");
                    assertThat(((NegocioException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void laAdmisionSeRevisaAntesQueElValor() {
        assertThatThrownBy(() -> exigirPagable(obligacion(false, TITULAR), new BigDecimal("1000.00")))
                .isExactlyInstanceOf(ConflictoException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo("NO_ADMITE_PAGOS"));
    }

    @Test
    void unValorDistintoDelExigibleSeRechazaPorqueNoHayAbonos() {
        assertThatThrownBy(() -> exigirPagable(obligacion(true, TITULAR), new BigDecimal("1000.00")))
                .isExactlyInstanceOf(NegocioException.class)
                .hasMessage("El valor debe ser exactamente el valor exigible de " + DocumentoPdf.pesos(EXIGIBLE)
                        + " (no se reciben abonos, P-05)")
                .satisfies(e -> {
                    assertThat(((NegocioException) e).getCodigo()).isEqualTo("VALOR_NO_COINCIDE");
                    assertThat(((NegocioException) e).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                });
    }

    @Test
    void sinValorTambienSeRechazaPorValor() {
        assertThatThrownBy(() -> exigirPagable(obligacion(true, TITULAR), null))
                .isExactlyInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo("VALOR_NO_COINCIDE"));
    }

    @Test
    void elValorExactoSeAceptaAunqueCambieLaEscala() {
        assertThatCode(() -> exigirPagable(obligacion(true, TITULAR), EXIGIBLE)).doesNotThrowAnyException();
        assertThatCode(() -> exigirPagable(obligacion(true, TITULAR), new BigDecimal("123620")))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ admisión en el pago en línea

    @Test
    void elPagoEnLineaRechazaUnaObligacionQueNoAdmitePagoSinAbrirIntentos() {
        Obligaciones obligaciones = mock(Obligaciones.class);
        TransaccionPasarelaRepository transacciones = mock(TransaccionPasarelaRepository.class);
        when(obligaciones.porId(TipoObligacion.FACTURA, 77L)).thenReturn(obligacion(false, TITULAR));

        assertThatThrownBy(() -> crearPagoEnLinea(obligaciones, transacciones)
                .iniciar(TITULAR, TipoObligacion.FACTURA, 77L, "127.0.0.1"))
                .isExactlyInstanceOf(ConflictoException.class)
                .hasMessage(MOTIVO)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo("NO_ADMITE_PAGOS"));
        verifyNoInteractions(transacciones);
    }

    @Test
    void elPagoEnLineaDeUnaObligacionAjenaNoRevelaSiExisteAntesDeRevisarLaAdmision() {
        Obligaciones obligaciones = mock(Obligaciones.class);
        TransaccionPasarelaRepository transacciones = mock(TransaccionPasarelaRepository.class);
        when(obligaciones.porId(TipoObligacion.FACTURA, 77L)).thenReturn(obligacion(false, TITULAR));

        assertThatThrownBy(() -> crearPagoEnLinea(obligaciones, transacciones)
                .iniciar(901L, TipoObligacion.FACTURA, 77L, "127.0.0.1"))
                .isExactlyInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(transacciones);
    }

    // ------------------------------------------------------------------ referencia de pago del DTO

    @ParameterizedTest
    @ValueSource(strings = {"FAC-202608-0000002", "fac-202608-0000002", "AP-2026-000001-03", "ap-2026-000001-03",
            "AP-2026-000001"})
    void laReferenciaDelPagoAceptaFacturasYCuotasSinImportarMayusculas(String referencia) {
        assertThat(violacionesDeReferencia(referencia)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAC-2026-0000002", "FAC-202608-000002", "AP-2026-000001-3", "AP-26-000001-03",
            "FAC-202608-0000002 ", "XYZ"})
    void laReferenciaDelPagoRechazaFormatosInvalidosConElMismoMensaje(String referencia) {
        assertThat(violacionesDeReferencia(referencia)).containsExactly(MENSAJE_REFERENCIA);
    }

    // ------------------------------------------------------------------ fixture

    /** Fixture: punto de entrada de la regla en caja y banco. */
    private static void exigirPagable(Obligacion o, BigDecimal valor) {
        PagoService.exigirPagable(o, valor);
    }

    /** Fixture: servicio de pago en línea con solo lo que usa el rechazo por admisión. */
    private static PagoEnLineaService crearPagoEnLinea(Obligaciones obligaciones,
            TransaccionPasarelaRepository transacciones) {
        return new PagoEnLineaService(transacciones, null, obligaciones, null, null, null, null);
    }

    private static List<String> violacionesDeReferencia(String referencia) {
        PagoController.PagoRequest solicitud = new PagoController.PagoRequest(referencia, CanalPago.PRESENCIAL,
                EXIGIBLE, null);
        return validador.validateProperty(solicitud, "referencia").stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    private static Obligacion obligacion(boolean admitePago, Long usuarioId) {
        return new Obligacion(TipoObligacion.FACTURA, 77L, "FAC-202608-0000002", usuarioId, "USR-00000001",
                "María Fernanda López Gómez", admitePago ? "PENDIENTE" : "PAGADA", EXIGIBLE, BigDecimal.ZERO,
                EXIGIBLE, LocalDate.of(2026, 9, 15), admitePago, admitePago ? null : MOTIVO, List.of());
    }
}
