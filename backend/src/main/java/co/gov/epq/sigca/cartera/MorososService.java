package co.gov.epq.sigca.cartera;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;

/** Reporte de usuarios morosos (RF-23, P-11) en pantalla y en CSV. */
@Service
public class MorososService {

    static final int MAX_EXPORTACION = 20_000;

    private final CarteraConsultas consultas;
    private final AccesoCartera acceso;
    private final Parametros parametros;
    private final Auditoria auditoria;

    public MorososService(CarteraConsultas consultas, AccesoCartera acceso, Parametros parametros, Auditoria auditoria) {
        this.consultas = consultas;
        this.acceso = acceso;
        this.parametros = parametros;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<CarteraVistas.Moroso> listar(CarteraConsultas.FiltroMorosos filtro, Pageable pageable) {
        CarteraConsultas.FiltroMorosos f = efectivo(filtro);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Sort.Order o = pageable.getSort().stream().findFirst().orElse(Sort.Order.desc("deuda"));
        long total = consultas.contarMorosos(f);
        List<CarteraVistas.Moroso> lista = consultas.morosos(f, o.getProperty(), o.isAscending(),
                (long) pageable.getPageNumber() * size, size, parametros.entero(Parametros.MORA_PERIODOS_SUSPENSION));
        return new PageImpl<>(lista, PageRequest.of(pageable.getPageNumber(), size), total);
    }

    /** CSV separado por punto y coma, UTF-8 con BOM (Excel en español). Audita EXPORTAR_REPORTE. */
    @Transactional
    public byte[] exportar(CarteraConsultas.FiltroMorosos filtro) {
        CarteraConsultas.FiltroMorosos f = efectivo(filtro);
        List<CarteraVistas.Moroso> lista = consultas.morosos(f, "deuda", false, 0, MAX_EXPORTACION,
                parametros.entero(Parametros.MORA_PERIODOS_SUSPENSION));
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Código;Nombre;Municipios;Facturas vencidas;Periodos en mora;Saldo vencido;En reclamo;Deuda exigible;")
                .append("Primer vencimiento;Días de mora;Estado cobro;Candidato suspensión;Aviso suspensión;Acuerdo vigente;")
                .append("Órdenes de suspensión pendientes\r\n");
        for (CarteraVistas.Moroso m : lista) {
            sb.append(celda(m.codigoUsuario())).append(';').append(celda(m.nombre())).append(';')
                    .append(celda(m.municipios())).append(';').append(m.facturasVencidas()).append(';')
                    .append(m.periodosMora()).append(';').append(numero(m.saldoVencido())).append(';')
                    .append(numero(m.valorEnReclamo())).append(';').append(numero(m.deudaExigible())).append(';')
                    .append(m.primerVencimiento()).append(';').append(m.diasMora()).append(';')
                    .append(m.estadoCobro() == null ? "SIN COBRO" : m.estadoCobro().name()).append(';')
                    .append(m.candidatoSuspension() ? "SI" : "NO").append(';')
                    .append(m.fechaAvisoSuspension() == null ? "" : m.fechaAvisoSuspension().toLocalDate()).append(';')
                    .append(m.acuerdoVigente() ? "SI" : "NO").append(';').append(m.ordenesSuspensionPendientes())
                    .append("\r\n");
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("reporte", "MOROSOS");
        d.put("registros", lista.size());
        d.put("municipioId", f.municipioId());
        d.put("antiguedad", f.antiguedad());
        d.put("cobro", f.cobro());
        auditoria.exito(OperacionAuditoria.EXPORTAR_REPORTE, "REPORTE", null, d);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private CarteraConsultas.FiltroMorosos efectivo(CarteraConsultas.FiltroMorosos f) {
        return new CarteraConsultas.FiltroMorosos(acceso.municipioEfectivo(f.municipioId()), f.antiguedad(), f.cobro(),
                f.texto());
    }

    /** Escapa comillas y neutraliza fórmulas (=, +, -, @) para evitar inyección en hojas de cálculo. */
    static String celda(String v) {
        if (v == null) {
            return "";
        }
        String t = v.replace("\r", " ").replace("\n", " ");
        if (!t.isEmpty() && "=+-@\t".indexOf(t.charAt(0)) >= 0) {
            t = "'" + t;
        }
        return "\"" + t.replace("\"", "\"\"") + "\"";
    }

    private static String numero(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
