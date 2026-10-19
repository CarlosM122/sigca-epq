package co.gov.epq.sigca.portal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import co.gov.epq.sigca.cartera.EstadoAcuerdo;
import co.gov.epq.sigca.cartera.EstadoCuota;
import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.lecturas.EstadoLectura;
import co.gov.epq.sigca.lecturas.OrigenLectura;

/** Vistas del portal ciudadano (RF-07, RF-26, RF-27). */
public final class PortalVistas {

    private PortalVistas() {
    }

    public record ContratoPortal(Long id, String numeroContrato, TipoServicio tipoServicio, EstadoContrato estado,
            LocalDate fechaVinculacion, Long predioId, String codigoPredio, String direccion, String municipio, String uso,
            Integer estrato, String serialMedidor) {
    }

    public record ConsumoPortal(Integer periodo, LocalDate fechaLectura, BigDecimal lecturaAnterior,
            BigDecimal lecturaActual, BigDecimal consumo, EstadoLectura estado, OrigenLectura origen) {
    }

    public record ConsumosContrato(Long contratoId, String numeroContrato, TipoServicio tipoServicio,
            String serialMedidor, String nota, BigDecimal promedio, List<ConsumoPortal> consumos) {
    }

    public record CuotaPortal(Long id, Integer numeroCuota, String referencia, BigDecimal valorCuota,
            LocalDate fechaVencimiento, EstadoCuota estado, LocalDateTime fechaPago, boolean pagable, String motivoNoPagable) {
    }

    public record AcuerdoPortal(Long id, String numeroAcuerdo, LocalDate fechaAcuerdo, BigDecimal totalIncluido,
            BigDecimal cuotaInicial, Integer numeroCuotas, EstadoAcuerdo estado, List<CuotaPortal> cuotas) {
    }

    public record Disponibilidad(LocalDate fechaEstimada) {
    }
}
