package co.gov.epq.sigca.facturacion;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.common.validation.SinHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Facturas (RF-05, RF-07, RF-20, RF-21, P-10) y lotes de facturación. */
@RestController
@RequestMapping("/api/facturas")
@Validated
public class FacturaController {

    private final FacturaService servicio;
    private final LoteFacturacionService lotes;
    private final FacturaPdf pdf;

    public FacturaController(FacturaService servicio, LoteFacturacionService lotes, FacturaPdf pdf) {
        this.servicio = servicio;
        this.lotes = lotes;
        this.pdf = pdf;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FACTURA_CONSULTAR')")
    public Page<FacturaResumen> buscar(
            @RequestParam(required = false) @Pattern(regexp = "^(?i)FAC-\\d{6}-\\d{7}$", message = "Número de factura inválido (FAC-AAAAMM-NNNNNNN)") String numero,
            @RequestParam(required = false) @Pattern(regexp = "^(?i)USR-\\d{8}$", message = "Código de usuario inválido (USR-NNNNNNNN)") String usuario,
            @RequestParam(required = false) @Pattern(regexp = "^(?i)PRD-\\d{8}$", message = "Código de predio inválido (PRD-NNNNNNNN)") String predio,
            @RequestParam(required = false) @Min(value = 200001, message = "Periodo inválido") @Max(value = 209912, message = "Periodo inválido") Integer periodo,
            @RequestParam(required = false) EstadoFactura estado,
            @RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) Long loteId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(FacturaService.TAMANO_MAXIMO) int size) {
        return servicio.buscar(new FacturaService.Filtro(numero, usuario, predio, periodo, estado, municipioId, loteId), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FACTURA_CONSULTAR')")
    public FacturaDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('FACTURA_CONSULTAR')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return pdfDe(servicio.detalle(id), pdf);
    }

    /** P-10: anula con motivo y, opcionalmente, reexpide en la misma operación. */
    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public FacturaService.ResultadoAnulacion anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest req) {
        return servicio.anular(id, req.motivo(), Boolean.TRUE.equals(req.reexpedir()), req.version());
    }

    @PostMapping("/{id}/reexpedicion")
    @PreAuthorize("hasAuthority('FACTURA_ANULAR')")
    public FacturaDetalle reexpedir(@PathVariable Long id) {
        return servicio.reexpedir(id);
    }

    /** RF-05: lanza o relanza el lote de un periodo cerrado (por defecto el último). */
    @PostMapping("/lotes")
    @PreAuthorize("hasAuthority('FACTURA_GENERAR_LOTE')")
    public LoteVista generarLote(@Valid @RequestBody(required = false) LoteRequest req) {
        return lotes.ejecutar(req == null ? null : req.periodo());
    }

    @GetMapping("/lotes")
    @PreAuthorize("hasAnyAuthority('FACTURA_GENERAR_LOTE','FACTURA_CONSULTAR')")
    public List<LoteVista> lotes(@RequestParam(defaultValue = "12") @Min(1) @Max(50) int cantidad) {
        return lotes.recientes(cantidad);
    }

    @GetMapping("/lotes/{id}")
    @PreAuthorize("hasAnyAuthority('FACTURA_GENERAR_LOTE','FACTURA_CONSULTAR')")
    public LoteVista lote(@PathVariable Long id) {
        return lotes.detalle(id);
    }

    public record AnulacionRequest(
            @NotBlank(message = "El motivo es obligatorio")
            @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres") @SinHtml String motivo,
            Boolean reexpedir,
            @NotNull(message = "Falta la versión de la factura") Long version) {

        public AnulacionRequest {
            motivo = motivo == null ? null : motivo.trim();
        }
    }

    public record LoteRequest(
            @Min(value = 200001, message = "Periodo inválido (AAAAMM)") @Max(value = 209912, message = "Periodo inválido (AAAAMM)") Integer periodo) {
    }

    /** Respuesta PDF descargable con el número de factura como nombre de archivo. */
    public static ResponseEntity<byte[]> pdfDe(FacturaDetalle f, FacturaPdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.numeroFactura() + ".pdf").build().toString())
                .body(pdf.generar(f));
    }
}
