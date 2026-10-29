package co.gov.epq.sigca.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.openpdf.text.pdf.PdfReader;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.cartera.AcuerdoPago;
import co.gov.epq.sigca.cartera.CuotaAcuerdo;
import co.gov.epq.sigca.cartera.EstadoAcuerdo;
import co.gov.epq.sigca.cartera.EstadoCuota;
import co.gov.epq.sigca.facturacion.DocumentoPdf;
import co.gov.epq.sigca.facturacion.EstadoFactura;
import co.gov.epq.sigca.facturacion.EstadoSyncErp;
import co.gov.epq.sigca.facturacion.Factura;
import co.gov.epq.sigca.facturacion.FacturaRepository;
import co.gov.epq.sigca.facturacion.FacturacionUsuariosRepository;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;
import co.gov.epq.sigca.usuarios.Usuario;
import jakarta.persistence.EntityManager;

/**
 * Pruebas de caracterización del registro de pagos (taller de refactorización, hallazgo H-01).
 *
 * <p>Fijan el comportamiento observable de {@code PagoService.aplicar} y del comprobante PDF <b>antes</b> de separar
 * responsabilidades: qué se guarda, en qué orden ocurre cada efecto dentro de la transacción, el texto exacto del
 * correo y el detalle exacto de la auditoría. No necesitan Oracle: los triggers se sustituyen por el refresco simulado
 * y el código de pago lo asigna el doble del repositorio.
 *
 * <p>Regla del taller: después de refactorizar solo pueden cambiar los métodos marcados como <i>fixture</i>
 * ({@link #crearServicio()} y {@link #comprobante(Long)}); las aserciones quedan intactas.
 */
class PagoServiceCaracterizacionTest {

    private static final Long USUARIO_ID = 7L;
    private static final Long FACTURA_ID = 40L;
    private static final Long CUOTA_ID = 90L;
    private static final Long ACUERDO_ID = 91L;
    private static final Long PAGO_ID = 500L;
    private static final String CODIGO_PAGO = "PAG-0000000001";
    private static final LocalDateTime FECHA_PAGO = LocalDateTime.of(2026, 10, 14, 9, 30);
    private static final BigDecimal VALOR = new BigDecimal("123620.00");

    private PagoRepository pagos;
    private Obligaciones obligaciones;
    private FacturaRepository facturas;
    private PagosCuotasRepository cuotas;
    private PagosAcuerdosRepository acuerdos;
    private FacturacionUsuariosRepository usuarios;
    private Auditoria auditoria;
    private Notificaciones notificaciones;
    private EntityManager em;

    private PagoService servicio;
    private Usuario titular;
    private Factura factura;
    private CuotaAcuerdo cuota;
    private AcuerdoPago acuerdo;
    private Pago guardado;

    @BeforeEach
    void preparar() {
        pagos = mock(PagoRepository.class);
        obligaciones = mock(Obligaciones.class);
        facturas = mock(FacturaRepository.class);
        cuotas = mock(PagosCuotasRepository.class);
        acuerdos = mock(PagosAcuerdosRepository.class);
        usuarios = mock(FacturacionUsuariosRepository.class);
        auditoria = mock(Auditoria.class);
        notificaciones = mock(Notificaciones.class);
        em = mock(EntityManager.class);

        titular = new Usuario();
        titular.setId(USUARIO_ID);
        titular.setCodigo("USR-00000001");
        titular.setNombreCompleto("María Fernanda López Gómez");
        titular.setCorreo("maria.lopez@example.com");
        when(usuarios.findById(USUARIO_ID)).thenReturn(Optional.of(titular));

        factura = new Factura();
        factura.setId(FACTURA_ID);
        factura.setNumeroFactura("FAC-202608-0000002");
        factura.setEstado(EstadoFactura.PAGADA);
        factura.setSaldoPendiente(BigDecimal.ZERO);
        factura.setValorEnReclamo(BigDecimal.ZERO);
        when(facturas.findById(FACTURA_ID)).thenReturn(Optional.of(factura));

        acuerdo = new AcuerdoPago();
        acuerdo.setId(ACUERDO_ID);
        acuerdo.setNumeroAcuerdo("AP-2026-000001");
        acuerdo.setUsuarioId(USUARIO_ID);
        acuerdo.setEstado(EstadoAcuerdo.VIGENTE);
        cuota = new CuotaAcuerdo();
        cuota.setId(CUOTA_ID);
        cuota.setAcuerdoId(ACUERDO_ID);
        cuota.setNumeroCuota(0);
        cuota.setValorCuota(new BigDecimal("20000.00"));
        cuota.setEstado(EstadoCuota.PAGADA);
        when(cuotas.findById(CUOTA_ID)).thenReturn(Optional.of(cuota));
        when(acuerdos.findById(ACUERDO_ID)).thenReturn(Optional.of(acuerdo));

        // Lo que hacen TRG_PAGO_BI / la secuencia al insertar: id, código y fecha del servidor
        when(pagos.saveAndFlush(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            p.setId(PAGO_ID);
            p.setCodigoPago(CODIGO_PAGO);
            p.setFechaPago(FECHA_PAGO);
            guardado = p;
            return p;
        });
        when(pagos.findById(PAGO_ID)).thenAnswer(inv -> Optional.ofNullable(guardado));

        servicio = crearServicio();
    }

    // ---------------------------------------------------------------- fixture (lo único que cambia al refactorizar)

    private PagoService crearServicio() {
        return new PagoService(pagos, obligaciones, facturas, cuotas, acuerdos, usuarios, auditoria,
                new AvisosPago(notificaciones), em);
    }

    private byte[] comprobante(Long pagoId) {
        return new ComprobantePagoPdf().generar(servicio.detalle(pagoId));
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void pagoPresencialDeFacturaGuardaElPagoConLosDatosDeLaObligacion() {
        servicio.aplicar(obligacionFactura(), CanalPago.PRESENCIAL, VALOR, 3L, null, null, null);

        Pago p = ultimoPagoGuardado();
        assertThat(p.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(p.getFacturaId()).isEqualTo(FACTURA_ID);
        assertThat(p.getCuotaId()).isNull();
        assertThat(p.getValor()).isEqualByComparingTo(VALOR);
        assertThat(p.getCanalPago()).isEqualTo(CanalPago.PRESENCIAL);
        assertThat(p.getFuncionarioId()).isEqualTo(3L);
        assertThat(p.getReferenciaBanco()).isNull();
        assertThat(p.getTransaccionPasarelaId()).isNull();
        assertThat(p.getCodigoTransaccion()).isNull();
        assertThat(p.getComprobanteEnviado()).isTrue();
        assertThat(p.getEstadoSyncErp()).isEqualTo(EstadoSyncErp.PENDIENTE);
    }

    @Test
    void losEfectosOcurrenEnOrdenGuardarRefrescarNotificarAuditar() {
        servicio.aplicar(obligacionFactura(), CanalPago.PRESENCIAL, VALOR, 3L, null, null, null);

        InOrder orden = inOrder(pagos, em, notificaciones, auditoria);
        orden.verify(pagos).saveAndFlush(any(Pago.class));
        orden.verify(em).refresh(factura);
        orden.verify(notificaciones).correo(any(), any(), any(), anyString(), anyString(), anyString(), anyLong());
        orden.verify(auditoria).exito(any(), anyString(), anyLong(), anyMap());
    }

    @Test
    void elCorreoDelComprobanteConservaDestinatarioAsuntoYCuerpo() {
        servicio.aplicar(obligacionFactura(), CanalPago.PRESENCIAL, VALOR, 3L, null, null, null);

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).correo(eq(EventoNotificacion.COMPROBANTE_PAGO), eq("maria.lopez@example.com"),
                eq(USUARIO_ID), eq("Comprobante de pago " + CODIGO_PAGO), cuerpo.capture(), eq("PAGO"), eq(PAGO_ID));
        assertThat(cuerpo.getValue()).isEqualTo("Hola María Fernanda López Gómez,\n\nRecibimos su pago.\n"
                + "Comprobante: PAG-0000000001\n"
                + "Referencia pagada: FAC-202608-0000002\n"
                + "Valor: " + DocumentoPdf.pesos(VALOR) + "\n"
                + "Fecha: " + DocumentoPdf.fechaHora(FECHA_PAGO) + "\n"
                + "Canal: PRESENCIAL"
                + "\n\nPuede descargar el comprobante en el portal de EPQ.\n");
    }

    @Test
    void elPagoEnLineaIncluyeLaTransaccionEnElCorreoYEnLaAuditoria() {
        servicio.aplicar(obligacionFactura(), CanalPago.EN_LINEA, VALOR, null, null, 77L, "TRX-123");

        Pago p = ultimoPagoGuardado();
        assertThat(p.getFuncionarioId()).isNull();
        assertThat(p.getTransaccionPasarelaId()).isEqualTo(77L);
        assertThat(p.getCodigoTransaccion()).isEqualTo("TRX-123");

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).correo(any(), any(), any(), anyString(), cuerpo.capture(), anyString(), anyLong());
        assertThat(cuerpo.getValue()).contains("Canal: EN_LINEA (transacción TRX-123)\n\n");

        assertThat(detalleAuditado()).containsExactly(
                Map.entry("codigoPago", CODIGO_PAGO),
                Map.entry("referencia", "FAC-202608-0000002"),
                Map.entry("valor", VALOR),
                Map.entry("canal", CanalPago.EN_LINEA),
                Map.entry("codigoTransaccion", "TRX-123"));
    }

    @Test
    void laAuditoriaDelPagoPresencialTieneLasClavesEnOrdenYSinOpcionales() {
        servicio.aplicar(obligacionFactura(), CanalPago.PRESENCIAL, VALOR, 3L, null, null, null);

        verify(auditoria).exito(eq(OperacionAuditoria.REGISTRAR_PAGO), eq("PAGO"), eq(PAGO_ID), anyMap());
        assertThat(detalleAuditado()).containsExactly(
                Map.entry("codigoPago", CODIGO_PAGO),
                Map.entry("referencia", "FAC-202608-0000002"),
                Map.entry("valor", VALOR),
                Map.entry("canal", CanalPago.PRESENCIAL));
    }

    @Test
    void elRecaudoBancarioAuditaLaReferenciaDelBanco() {
        servicio.aplicar(obligacionFactura(), CanalPago.BANCO, VALOR, 3L, "BCO-778812", null, null);

        assertThat(ultimoPagoGuardado().getReferenciaBanco()).isEqualTo("BCO-778812");
        assertThat(detalleAuditado()).containsExactly(
                Map.entry("codigoPago", CODIGO_PAGO),
                Map.entry("referencia", "FAC-202608-0000002"),
                Map.entry("valor", VALOR),
                Map.entry("canal", CanalPago.BANCO),
                Map.entry("referenciaBanco", "BCO-778812"));
    }

    @Test
    void sinCorreoElComprobanteNoQuedaMarcadoComoEnviadoYLaColaRecibeElDestinoEnBlanco() {
        titular.setCorreo("  ");
        servicio.aplicar(obligacionFactura(), CanalPago.PRESENCIAL, VALOR, 3L, null, null, null);

        assertThat(ultimoPagoGuardado().getComprobanteEnviado()).isFalse();
        // Notificaciones.correo es quien descarta el destino vacío; el servicio igual lo invoca
        verify(notificaciones).correo(eq(EventoNotificacion.COMPROBANTE_PAGO), eq("  "), eq(USUARIO_ID), anyString(),
                anyString(), eq("PAGO"), eq(PAGO_ID));
    }

    @Test
    void elPagoDeUnaCuotaRefrescaLaCuotaYElAcuerdo() {
        servicio.aplicar(obligacionCuota(), CanalPago.PRESENCIAL, new BigDecimal("20000.00"), 3L, null, null, null);

        Pago p = ultimoPagoGuardado();
        assertThat(p.getCuotaId()).isEqualTo(CUOTA_ID);
        assertThat(p.getFacturaId()).isNull();
        InOrder orden = inOrder(em);
        orden.verify(em).refresh(cuota);
        orden.verify(em).refresh(acuerdo);
        verify(facturas, never()).findById(anyLong());

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).correo(any(), any(), any(), anyString(), cuerpo.capture(), anyString(), anyLong());
        assertThat(cuerpo.getValue()).contains("Referencia pagada: AP-2026-000001-00\n");
    }

    @Test
    void elComprobantePdfConservaTituloYDatosDelPago() throws Exception {
        servicio.aplicar(obligacionFactura(), CanalPago.BANCO, VALOR, 3L, "BCO-778812", null, null);

        byte[] pdf = comprobante(PAGO_ID);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        PdfReader lector = new PdfReader(pdf);
        try {
            assertThat(lector.getNumberOfPages()).isEqualTo(1);
            assertThat(lector.getInfo().get("Title")).isEqualTo("Comprobante de pago " + CODIGO_PAGO);
            String contenido = new String(lector.getPageContent(1), StandardCharsets.ISO_8859_1);
            assertThat(contenido).contains(CODIGO_PAGO, "FAC-202608-0000002", "BANCO", "BCO-778812",
                    "PAGADA", "Factura");
        } finally {
            lector.close();
        }
    }

    @Test
    void laVistaDelPagoNoCambia() {
        servicio.aplicar(obligacionFactura(), CanalPago.PRESENCIAL, VALOR, 3L, null, null, null);

        PagoVista v = servicio.detalle(PAGO_ID);

        assertThat(v).isEqualTo(new PagoVista(PAGO_ID, CODIGO_PAGO, TipoObligacion.FACTURA, FACTURA_ID,
                "FAC-202608-0000002", "USR-00000001", "María Fernanda López Gómez", VALOR, CanalPago.PRESENCIAL,
                FECHA_PAGO, null, null, "PAGADA", BigDecimal.ZERO, BigDecimal.ZERO, true));
    }

    // ---------------------------------------------------------------- apoyo

    private Obligacion obligacionFactura() {
        return new Obligacion(TipoObligacion.FACTURA, FACTURA_ID, "FAC-202608-0000002", USUARIO_ID, "USR-00000001",
                "María Fernanda López Gómez", "PENDIENTE", VALOR, BigDecimal.ZERO, VALOR, LocalDate.of(2026, 10, 29),
                true, null, List.of());
    }

    private Obligacion obligacionCuota() {
        BigDecimal v = new BigDecimal("20000.00");
        return new Obligacion(TipoObligacion.CUOTA, CUOTA_ID, "AP-2026-000001-00", USUARIO_ID, "USR-00000001",
                "María Fernanda López Gómez", "PENDIENTE", v, BigDecimal.ZERO, v, LocalDate.of(2026, 10, 29), true,
                null, List.of());
    }

    /** El pago que recibió el repositorio; además exige que se haya guardado exactamente una vez. */
    private Pago ultimoPagoGuardado() {
        verify(pagos, times(1)).saveAndFlush(any(Pago.class));
        assertThat(guardado).isNotNull();
        return guardado;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detalleAuditado() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).exito(eq(OperacionAuditoria.REGISTRAR_PAGO), eq("PAGO"), eq(PAGO_ID), captor.capture());
        return captor.getValue();
    }
}
