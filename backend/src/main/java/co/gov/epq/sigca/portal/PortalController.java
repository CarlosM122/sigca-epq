package co.gov.epq.sigca.portal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.facturacion.FacturaController;
import co.gov.epq.sigca.facturacion.FacturaDetalle;
import co.gov.epq.sigca.facturacion.FacturaPdf;
import co.gov.epq.sigca.facturacion.FacturaService;
import co.gov.epq.sigca.pagos.InicioPagoVista;
import co.gov.epq.sigca.pagos.PagoController;
import co.gov.epq.sigca.pagos.PagoEnLineaService;
import co.gov.epq.sigca.pagos.PagoResumen;
import co.gov.epq.sigca.pagos.PagoService;
import co.gov.epq.sigca.pagos.TipoObligacion;
import co.gov.epq.sigca.portal.PortalVistas.AcuerdoPortal;
import co.gov.epq.sigca.portal.PortalVistas.ConsumosContrato;
import co.gov.epq.sigca.portal.PortalVistas.ContratoPortal;
import co.gov.epq.sigca.portal.PortalVistas.Disponibilidad;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Portal ciudadano: contratos, consumos, facturas, acuerdos, pagos y pago en línea del titular autenticado. */
@RestController
@RequestMapping("/api/portal")
@Validated
public class PortalController {

    private final PortalService servicio;
    private final FacturaService facturas;
    private final FacturaPdf pdf;
    private final PagoService pagos;
    private final PagoEnLineaService enLinea;

    public PortalController(PortalService servicio, FacturaService facturas, FacturaPdf pdf, PagoService pagos,
            PagoEnLineaService enLinea) {
        this.servicio = servicio;
        this.facturas = facturas;
        this.pdf = pdf;
        this.pagos = pagos;
        this.enLinea = enLinea;
    }

    private static Long titular() {
        return UsuarioActual.ciudadano().usuarioId();
    }

    @GetMapping("/contratos")
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public List<ContratoPortal> contratos() {
        return servicio.contratos(titular());
    }

    @GetMapping("/consumos")
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public ConsumosContrato consumos(@RequestParam @NotNull Long contratoId) {
        return servicio.consumos(titular(), contratoId);
    }

    @GetMapping("/facturas")
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public List<FacturaDetalle> facturas() {
        return servicio.facturas(titular());
    }

    /** RF-05: fecha estimada en que estará disponible la próxima factura. */
    @GetMapping("/facturas/disponibilidad")
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public Disponibilidad disponibilidad() {
        return new Disponibilidad(facturas.fechaEstimadaDisponibilidad());
    }

    @GetMapping("/facturas/{id}")
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public FacturaDetalle factura(@PathVariable Long id) {
        return servicio.factura(titular(), id);
    }

    @GetMapping(value = "/facturas/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public ResponseEntity<byte[]> facturaPdf(@PathVariable Long id) {
        return FacturaController.pdfDe(servicio.factura(titular(), id), pdf);
    }

    @GetMapping("/acuerdos")
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public List<AcuerdoPortal> acuerdos() {
        return servicio.acuerdos(titular());
    }

    @GetMapping("/pagos")
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public Page<PagoResumen> pagos(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PagoService.TAMANO_MAXIMO) int size) {
        return servicio.pagos(titular(), page, size);
    }

    @GetMapping(value = "/pagos/{id}/comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('PORTAL_CONSULTAR')")
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        String codigo = servicio.codigoPagoPropio(titular(), id);
        return PagoController.pdf(codigo, pagos.comprobante(id));
    }

    /** CU-04: crea el intento de pago en línea y devuelve la URL de la pasarela. */
    @PostMapping("/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PORTAL_PAGAR')")
    public InicioPagoVista pagar(@Valid @RequestBody PagoEnLineaRequest req, HttpServletRequest http) {
        return enLinea.iniciar(titular(), req.tipoObligacion(), req.obligacionId(), http.getRemoteAddr());
    }

    public record PagoEnLineaRequest(@NotNull(message = "Indique qué va a pagar") TipoObligacion tipoObligacion,
            @NotNull(message = "Indique qué va a pagar") Long obligacionId) {
    }
}
