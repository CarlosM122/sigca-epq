package co.gov.epq.sigca.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Respuesta del tablero gerencial (RF-24, P-28). */
public final class DashboardVistas {

    private DashboardVistas() {
    }

    /** Cartera vencida por antigüedad (días desde el vencimiento). */
    public record CarteraPorEdad(BigDecimal hasta30, BigDecimal de31a60, BigDecimal de61a90, BigDecimal mas90) {
    }

    public record Indicadores(BigDecimal facturado, int facturas, BigDecimal recaudado, BigDecimal porcentajeRecaudo,
            BigDecimal recaudoMes, BigDecimal carteraVencida, CarteraPorEdad carteraPorEdad, int usuariosEnMora,
            int acuerdosVigentes, int pqrAbiertas, int pqrVencidas, Map<String, Integer> pqrAbiertasPorTipo,
            int pqrRespondidas12m, BigDecimal tiempoMedioRespuestaDias, int respuestasExtemporaneas12m,
            int silenciosSinReconocer, int contratosActivos, int contratosSuspendidos, int lecturasEnRevision) {
    }

    public record Municipio(Long municipioId, String municipio, Indicadores indicadores) {
    }

    public record PuntoTendencia(int periodo, BigDecimal facturado, BigDecimal recaudado) {
    }

    public record Tablero(int periodo, Long municipioId, LocalDateTime generadoEn, int refrescoSegundos,
            Indicadores global, List<Municipio> porMunicipio, List<PuntoTendencia> tendencia) {
    }
}
