package co.gov.epq.sigca.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.seguridad.UsuarioActual;

/** Tablero gerencial (RF-24, P-28): indicadores por municipio y consolidados. */
@Service
public class DashboardService {

    static final List<String> TIPOS = List.of("PETICION", "QUEJA", "RECLAMO", "RECURSO");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final DashboardConsultas consultas;
    private final Parametros parametros;

    public DashboardService(DashboardConsultas consultas, Parametros parametros) {
        this.consultas = consultas;
        this.parametros = parametros;
    }

    /**
     * @param periodo AAAAMM; por defecto el mes anterior (último facturado).
     * @param municipioId filtro; un funcionario con municipio asignado solo ve el suyo.
     */
    @Transactional(readOnly = true)
    public DashboardVistas.Tablero tablero(Integer periodo, Long municipioId) {
        Long propio = UsuarioActual.funcionario().municipioId();
        if (propio != null && municipioId != null && !propio.equals(municipioId)) {
            throw new NegocioException("ACCESO_DENEGADO", "Solo puede consultar el tablero de su municipio", HttpStatus.FORBIDDEN);
        }
        Long mun = propio != null ? propio : municipioId;
        int p = periodo == null ? Periodos.anterior() : periodo;
        Map<Long, DashboardConsultas.Acum> acum = new LinkedHashMap<>();
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (DashboardConsultas.MunicipioFila m : consultas.municipios(mun)) {
            acum.put(m.id(), new DashboardConsultas.Acum());
            nombres.put(m.id(), m.nombre());
        }
        consultas.cargar(acum, p);

        DashboardConsultas.Acum total = new DashboardConsultas.Acum();
        List<DashboardVistas.Municipio> lista = new ArrayList<>();
        for (Map.Entry<Long, DashboardConsultas.Acum> e : acum.entrySet()) {
            sumar(total, e.getValue());
            lista.add(new DashboardVistas.Municipio(e.getKey(), nombres.get(e.getKey()), indicadores(e.getValue())));
        }
        int desde = Periodos.dePeriodo(YearMonth.from(Periodos.aMes(p)).minusMonths(11));
        return new DashboardVistas.Tablero(p, mun, LocalDateTime.now(Periodos.ZONA),
                parametros.entero(Parametros.DASHBOARD_REFRESCO_SEG), indicadores(total), lista,
                consultas.tendencia(desde, p, mun));
    }

    static void sumar(DashboardConsultas.Acum t, DashboardConsultas.Acum a) {
        t.facturado = t.facturado.add(a.facturado);
        t.facturas += a.facturas;
        t.recaudado = t.recaudado.add(a.recaudado);
        t.recaudoMes = t.recaudoMes.add(a.recaudoMes);
        t.c30 = t.c30.add(a.c30);
        t.c60 = t.c60.add(a.c60);
        t.c90 = t.c90.add(a.c90);
        t.cMas = t.cMas.add(a.cMas);
        t.usuariosMora += a.usuariosMora;
        t.acuerdos += a.acuerdos;
        t.pqrAbiertas += a.pqrAbiertas;
        t.pqrVencidas += a.pqrVencidas;
        a.porTipo.forEach((k, v) -> t.porTipo.merge(k, v, Integer::sum));
        t.respondidas += a.respondidas;
        t.diasRespuesta = t.diasRespuesta.add(a.diasRespuesta);
        t.extemporaneas += a.extemporaneas;
        t.silencios += a.silencios;
        t.activos += a.activos;
        t.suspendidos += a.suspendidos;
        t.lecturas += a.lecturas;
    }

    static DashboardVistas.Indicadores indicadores(DashboardConsultas.Acum a) {
        BigDecimal pct = a.facturado.signum() == 0 ? BigDecimal.ZERO
                : a.recaudado.multiply(CIEN).divide(a.facturado, 2, RoundingMode.HALF_UP);
        BigDecimal medio = a.respondidas == 0 ? BigDecimal.ZERO
                : a.diasRespuesta.divide(BigDecimal.valueOf(a.respondidas), 1, RoundingMode.HALF_UP);
        Map<String, Integer> tipos = new TreeMap<>();
        TIPOS.forEach(t -> tipos.put(t, a.porTipo.getOrDefault(t, 0)));
        return new DashboardVistas.Indicadores(a.facturado, a.facturas, a.recaudado, pct, a.recaudoMes,
                a.c30.add(a.c60).add(a.c90).add(a.cMas), new DashboardVistas.CarteraPorEdad(a.c30, a.c60, a.c90, a.cMas),
                a.usuariosMora, a.acuerdos, a.pqrAbiertas, a.pqrVencidas, tipos, a.respondidas, medio, a.extemporaneas,
                a.silencios, a.activos, a.suspendidos, a.lecturas);
    }
}
