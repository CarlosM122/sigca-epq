package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import co.gov.epq.sigca.common.validation.SinHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Pagos presenciales y bancarios (RF-06, P-05, P-23), consulta de pagos y comprobantes (SWR-08). */
@RestController
@RequestMapping("/api/pagos")
@Validated
public class PagoController {

    private final PagoService servicio;
    private final Obligaciones obligaciones;
    private final ComprobantePagoPdf comprobantes;

    public PagoController(PagoService servicio, Obligaciones obligaciones, ComprobantePagoPdf comprobantes) {
        this.servicio = servicio;
        this.obligaciones = obligaciones;
        this.comprobantes = comprobantes;
    }

    /** Lo que se va a pagar con una referencia: total, valor en reclamo y valor exigible. */
    @GetMapping("/obligacion")
    @PreAuthorize("hasAuthority('PAGO_REGISTRAR_PRESENCIAL')")
    public Obligacion obligacion(@RequestParam @NotBlank String referencia) {
        return obligaciones.porReferencia(referencia);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAGO_REGISTRAR_PRESENCIAL')")
    public PagoVista registrar(@Valid @RequestBody PagoRequest req) {
        return servicio.registrarPresencial(req.referencia(), req.canal(), req.valor(), req.referenciaBanco());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGO_CONSULTAR')")
    public Page<PagoResumen> buscar(
            @RequestParam(required = false) @Pattern(regexp = "^(?i)PAG-\\d{10}$", message = "Código de pago inválido") String codigo,
            @RequestParam(required = false) @Pattern(regexp = "^(?i)USR-\\d{8}$", message = "Código de usuario inválido (USR-NNNNNNNN)") String usuario,
            @RequestParam(required = false) @Pattern(regexp = "^(?i)FAC-\\d{6}-\\d{7}$", message = "Número de factura inválido") String factura,
            @RequestParam(required = false) CanalPago canal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PagoService.TAMANO_MAXIMO) int size) {
        return servicio.buscar(new PagoService.Filtro(codigo, usuario, null, factura, canal, desde, hasta), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGO_CONSULTAR')")
    public PagoVista detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @GetMapping(value = "/{id}/comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('PAGO_CONSULTAR','PAGO_REGISTRAR_PRESENCIAL')")
    public ResponseEntity<byte[]> comprobante(@PathVariable Long id) {
        PagoVista v = servicio.detalle(id);
        return pdf(v.codigoPago(), comprobantes.generar(v));
    }

    public static ResponseEntity<byte[]> pdf(String nombre, byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre + ".pdf").build().toString())
                .body(contenido);
    }

    public record PagoRequest(
            @NotBlank(message = "La referencia es obligatoria")
            @Pattern(regexp = Obligaciones.PATRON_REFERENCIA, flags = Pattern.Flag.CASE_INSENSITIVE,
                    message = "Referencia inválida (FAC-AAAAMM-NNNNNNN o AP-AAAA-NNNNNN[-NN])") String referencia,
            @NotNull(message = "Indique el canal") CanalPago canal,
            @NotNull(message = "Indique el valor") @DecimalMin(value = "0.01", message = "El valor debe ser positivo")
            @Digits(integer = 12, fraction = 2, message = "Valor inválido") BigDecimal valor,
            @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "Referencia bancaria inválida") @SinHtml String referenciaBanco) {
    }
}
