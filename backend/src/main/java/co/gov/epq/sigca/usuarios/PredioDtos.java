package co.gov.epq.sigca.usuarios;

import java.util.List;

import co.gov.epq.sigca.common.validation.SinHtml;
import co.gov.epq.sigca.contratos.ContratoDtos.ContratoVista;
import co.gov.epq.sigca.contratos.ContratoDtos.MedidorVista;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contratos de la API de predios (RF-18, P-06). */
public final class PredioDtos {

    private PredioDtos() {
    }

    /** Crear o modificar un predio. El sector, si se indica, debe ser del mismo municipio. */
    @EstratoSegunUso
    public record PredioDatos(
            @Pattern(regexp = Textos.FICHA_CATASTRAL, message = "La ficha catastral debe tener exactamente 30 dígitos") String fichaCatastral,
            @NotBlank(message = "La dirección es obligatoria")
            @Pattern(regexp = Textos.TEXTO_5_200, message = "La dirección debe tener entre 5 y 200 caracteres") @SinHtml String direccion,
            @Size(max = 100, message = "Máximo 100 caracteres") @SinHtml String barrioVereda,
            @NotNull(message = "El municipio es obligatorio") Long municipioId,
            Long sectorId,
            @NotNull(message = "El uso es obligatorio") UsoPredio uso,
            Integer estrato,
            Long version) implements EstratoSegunUso.ConEstrato {
    }

    public record PredioVista(Long id, String codigoPredio, String fichaCatastral, String direccion, String barrioVereda,
            Long municipioId, String municipio, Long sectorId, String sector, UsoPredio uso, Integer estrato, Long version) {
    }

    public record PredioDetalle(PredioVista predio, List<MedidorVista> medidores, List<ContratoVista> contratos) {
    }

}
