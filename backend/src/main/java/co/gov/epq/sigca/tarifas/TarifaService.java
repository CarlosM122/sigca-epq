package co.gov.epq.sigca.tarifas;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.tarifas.TarifaDtos.Cierre;
import co.gov.epq.sigca.tarifas.TarifaDtos.FactorDatos;
import co.gov.epq.sigca.tarifas.TarifaDtos.FactorVista;
import co.gov.epq.sigca.tarifas.TarifaDtos.NuevaVigencia;
import co.gov.epq.sigca.tarifas.TarifaDtos.TarifaDatos;
import co.gov.epq.sigca.tarifas.TarifaDtos.TarifaVista;
import co.gov.epq.sigca.usuarios.CatalogoTerritorial;
import co.gov.epq.sigca.usuarios.UsoPredio;
import co.gov.epq.sigca.usuarios.Versiones;

/**
 * Tarifas y factores de subsidio (P-08, RN-08, RN-37). Una tarifa ya registrada no se modifica (TRG_TARIFA_BIU):
 * solo se cierra su vigencia. Cerrar la vigencia abierta y crear la siguiente queda aquí (README, decisión 10),
 * en una sola transacción.
 */
@Service
public class TarifaService {

    static final String TARIFA = "TARIFA";
    static final String FACTOR = "FACTOR_SUBSIDIO";

    private final TarifaRepository tarifas;
    private final FactorSubsidioRepository factores;
    private final CatalogoTerritorial catalogo;
    private final Auditoria auditoria;

    public TarifaService(TarifaRepository tarifas, FactorSubsidioRepository factores, CatalogoTerritorial catalogo,
            Auditoria auditoria) {
        this.tarifas = tarifas;
        this.factores = factores;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
    }

    // ----------------------------------------------------------------- tarifas

    @Transactional(readOnly = true)
    public Page<TarifaVista> buscarTarifas(Long municipioId, TipoServicio tipo, LocalDate vigenteEn, Pageable pageable) {
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        return tarifas.buscar(municipioId, tipo, vigenteEn, pageable).map(t -> vista(t, municipios));
    }

    /** Crea una tarifa cuando no hay otra abierta ni superpuesta para el mismo servicio y municipio. */
    @Transactional
    public TarifaVista crearTarifa(TarifaDatos req) {
        catalogo.exigirMunicipioActivo(req.municipioId());
        if (tarifas.seSuperpone(req.tipoServicio(), req.municipioId(), req.vigenteDesde(), null)) {
            throw new ConflictoException("VIGENCIA_SUPERPUESTA", "Ya hay una tarifa vigente para ese servicio y municipio en esa fecha: "
                    + "cierre su vigencia o use \"nueva vigencia\"");
        }
        return vista(insertar(req), catalogo.nombresMunicipio());
    }

    /** Cierra la tarifa abierta el día anterior a la nueva y crea la nueva, en la misma transacción. */
    @Transactional
    public NuevaVigencia<TarifaVista> nuevaVigenciaTarifa(TarifaDatos req) {
        catalogo.exigirMunicipioActivo(req.municipioId());
        Tarifa abierta = tarifas.findFirstByTipoServicioAndMunicipioIdAndVigenteHastaIsNull(req.tipoServicio(), req.municipioId())
                .orElse(null);
        if (abierta != null) {
            if (!req.vigenteDesde().isAfter(abierta.getVigenteDesde())) {
                throw new NegocioException("VIGENCIA_INVALIDA", "La nueva vigencia debe empezar después del "
                        + abierta.getVigenteDesde() + " (inicio de la tarifa abierta)", HttpStatus.UNPROCESSABLE_CONTENT);
            }
            cerrar(abierta, req.vigenteDesde().minusDays(1));
        }
        if (tarifas.seSuperpone(req.tipoServicio(), req.municipioId(), req.vigenteDesde(), abierta == null ? null : abierta.getId())) {
            throw new ConflictoException("VIGENCIA_SUPERPUESTA", "La nueva vigencia se superpone con una tarifa registrada");
        }
        Tarifa nueva = insertar(req);
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        return new NuevaVigencia<>(abierta == null ? null : vista(abierta, municipios), vista(nueva, municipios));
    }

    @Transactional
    public TarifaVista cerrarTarifa(Long id, Cierre req) {
        Tarifa t = tarifas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Tarifa", id));
        Versiones.exigir(req.version(), t.getVersion());
        if (t.getVigenteHasta() != null) {
            throw new ConflictoException("VIGENCIA_CERRADA", "La tarifa ya tiene cerrada su vigencia el " + t.getVigenteHasta());
        }
        cerrar(t, req.vigenteHasta());
        return vista(t, catalogo.nombresMunicipio());
    }

    private Tarifa insertar(TarifaDatos req) {
        Tarifa t = new Tarifa();
        t.setTipoServicio(req.tipoServicio());
        t.setMunicipioId(req.municipioId());
        t.setCargoFijo(req.cargoFijo());
        t.setValorM3Basico(req.valorM3Basico());
        t.setValorM3Complementario(req.valorM3Complementario());
        t.setValorM3Suntuario(req.valorM3Suntuario());
        t.setConsumoBasicoM3(req.consumoBasicoM3());
        t.setConsumoSuntuarioDesdeM3(req.consumoSuntuarioDesdeM3());
        t.setVigenteDesde(req.vigenteDesde());
        t.setResolucion(Normalizador.texto(req.resolucion()));
        tarifas.saveAndFlush(t);
        auditoria.exito(OperacionAuditoria.CREAR_TARIFA, TARIFA, t.getId(), Map.of("tipoServicio", t.getTipoServicio(),
                "municipioId", t.getMunicipioId(), "vigenteDesde", t.getVigenteDesde().toString(), "resolucion", t.getResolucion()));
        return t;
    }

    private void cerrar(Tarifa t, LocalDate hasta) {
        if (hasta.isBefore(t.getVigenteDesde())) {
            throw new NegocioException("VIGENCIA_INVALIDA", "La vigencia no puede terminar antes de empezar (" + t.getVigenteDesde() + ")");
        }
        t.setVigenteHasta(hasta);
        tarifas.saveAndFlush(t);
        auditoria.exito(OperacionAuditoria.MODIFICAR_TARIFA, TARIFA, t.getId(), Map.of("vigenteHasta", hasta.toString()));
    }

    // ----------------------------------------------------------------- factores

    @Transactional(readOnly = true)
    public Page<FactorVista> buscarFactores(Long municipioId, TipoServicio tipo, UsoPredio uso, LocalDate vigenteEn,
            Pageable pageable) {
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        return factores.buscar(municipioId, tipo, uso, vigenteEn, pageable).map(f -> vista(f, municipios));
    }

    @Transactional
    public FactorVista crearFactor(FactorDatos req) {
        catalogo.exigirMunicipioActivo(req.municipioId());
        Integer estrato = estrato(req);
        if (factores.seSuperpone(req.municipioId(), req.tipoServicio(), req.uso(), estrato, req.vigenteDesde(), null)) {
            throw new ConflictoException("VIGENCIA_SUPERPUESTA", "Ya hay un factor vigente para ese servicio, uso y estrato en esa fecha: "
                    + "cierre su vigencia o use \"nueva vigencia\"");
        }
        return vista(insertar(req), catalogo.nombresMunicipio());
    }

    @Transactional
    public NuevaVigencia<FactorVista> nuevaVigenciaFactor(FactorDatos req) {
        catalogo.exigirMunicipioActivo(req.municipioId());
        Integer estrato = estrato(req);
        FactorSubsidio abierto = factores.abierto(req.municipioId(), req.tipoServicio(), req.uso(), estrato).orElse(null);
        if (abierto != null) {
            if (!req.vigenteDesde().isAfter(abierto.getVigenteDesde())) {
                throw new NegocioException("VIGENCIA_INVALIDA", "La nueva vigencia debe empezar después del "
                        + abierto.getVigenteDesde() + " (inicio del factor abierto)");
            }
            cerrar(abierto, req.vigenteDesde().minusDays(1));
        }
        if (factores.seSuperpone(req.municipioId(), req.tipoServicio(), req.uso(), estrato, req.vigenteDesde(),
                abierto == null ? null : abierto.getId())) {
            throw new ConflictoException("VIGENCIA_SUPERPUESTA", "La nueva vigencia se superpone con un factor registrado");
        }
        FactorSubsidio nuevo = insertar(req);
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        return new NuevaVigencia<>(abierto == null ? null : vista(abierto, municipios), vista(nuevo, municipios));
    }

    @Transactional
    public FactorVista cerrarFactor(Long id, Cierre req) {
        FactorSubsidio f = factores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Factor de subsidio", id));
        Versiones.exigir(req.version(), f.getVersion());
        if (f.getVigenteHasta() != null) {
            throw new ConflictoException("VIGENCIA_CERRADA", "El factor ya tiene cerrada su vigencia el " + f.getVigenteHasta());
        }
        cerrar(f, req.vigenteHasta());
        return vista(f, catalogo.nombresMunicipio());
    }

    private FactorSubsidio insertar(FactorDatos req) {
        FactorSubsidio f = new FactorSubsidio();
        f.setMunicipioId(req.municipioId());
        f.setTipoServicio(req.tipoServicio());
        f.setUso(req.uso());
        f.setEstrato(estrato(req));
        f.setPorcentaje(req.porcentaje());
        f.setVigenteDesde(req.vigenteDesde());
        f.setAcuerdoMunicipal(Normalizador.texto(req.acuerdoMunicipal()));
        factores.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.CREAR_FACTOR_SUBSIDIO, FACTOR, f.getId(), Map.of("tipoServicio", f.getTipoServicio(),
                "municipioId", f.getMunicipioId(), "uso", f.getUso(), "porcentaje", f.getPorcentaje(),
                "vigenteDesde", f.getVigenteDesde().toString()));
        return f;
    }

    private void cerrar(FactorSubsidio f, LocalDate hasta) {
        if (hasta.isBefore(f.getVigenteDesde())) {
            throw new NegocioException("VIGENCIA_INVALIDA", "La vigencia no puede terminar antes de empezar (" + f.getVigenteDesde() + ")");
        }
        f.setVigenteHasta(hasta);
        factores.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.MODIFICAR_FACTOR_SUBSIDIO, FACTOR, f.getId(), Map.of("vigenteHasta", hasta.toString()));
    }

    private static Integer estrato(FactorDatos req) {
        return req.uso() == UsoPredio.RESIDENCIAL ? req.estrato() : null;
    }

    // ----------------------------------------------------------------- vistas

    private static boolean vigenteHoy(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = Periodos.hoy();
        return !desde.isAfter(hoy) && (hasta == null || !hasta.isBefore(hoy));
    }

    private static TarifaVista vista(Tarifa t, Map<Long, String> municipios) {
        return new TarifaVista(t.getId(), t.getTipoServicio(), t.getMunicipioId(), municipios.get(t.getMunicipioId()),
                t.getCargoFijo(), t.getValorM3Basico(), t.getValorM3Complementario(), t.getValorM3Suntuario(),
                t.getConsumoBasicoM3(), t.getConsumoSuntuarioDesdeM3(), t.getVigenteDesde(), t.getVigenteHasta(),
                t.getResolucion(), vigenteHoy(t.getVigenteDesde(), t.getVigenteHasta()), t.getCreadoPor(), t.getCreadoEn(),
                t.getVersion());
    }

    private static FactorVista vista(FactorSubsidio f, Map<Long, String> municipios) {
        return new FactorVista(f.getId(), f.getMunicipioId(), municipios.get(f.getMunicipioId()), f.getTipoServicio(), f.getUso(),
                f.getEstrato(), f.getPorcentaje(), f.getVigenteDesde(), f.getVigenteHasta(), f.getAcuerdoMunicipal(),
                vigenteHoy(f.getVigenteDesde(), f.getVigenteHasta()), f.getCreadoPor(), f.getCreadoEn(), f.getVersion());
    }

}
