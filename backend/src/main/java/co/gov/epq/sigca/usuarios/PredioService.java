package co.gov.epq.sigca.usuarios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.contratos.ContratoService;
import co.gov.epq.sigca.contratos.MedidorService;
import co.gov.epq.sigca.usuarios.PredioDtos.PredioDatos;
import co.gov.epq.sigca.usuarios.PredioDtos.PredioDetalle;
import co.gov.epq.sigca.usuarios.PredioDtos.PredioVista;

/** RF-18: predios con ficha catastral IGAC opcional, sector del mismo municipio y estrato según el uso (P-06). */
@Service
public class PredioService {

    static final String ENTIDAD = "PREDIO";

    private final PredioRepository predios;
    private final MedidorService medidores;
    private final ContratoService contratos;
    private final CatalogoTerritorial catalogo;
    private final Auditoria auditoria;

    public PredioService(PredioRepository predios, MedidorService medidores, ContratoService contratos,
            CatalogoTerritorial catalogo, Auditoria auditoria) {
        this.predios = predios;
        this.medidores = medidores;
        this.contratos = contratos;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
    }

    @Transactional
    public PredioVista crear(PredioDatos req) {
        validar(req, null);
        Predio p = new Predio();
        aplicar(p, req);
        predios.saveAndFlush(p);
        auditoria.exito(OperacionAuditoria.CREAR_PREDIO, ENTIDAD, p.getId(),
                Map.of("codigo", p.getCodigoPredio(), "municipioId", p.getMunicipioId(), "uso", p.getUso()));
        return vista(p);
    }

    @Transactional
    public PredioVista modificar(Long id, PredioDatos req) {
        Predio p = obtener(id);
        Versiones.exigir(req.version(), p.getVersion());
        validar(req, p);
        Map<String, Object> antes = foto(p);
        aplicar(p, req);
        Map<String, Object> despues = foto(p);
        List<String> cambios = antes.keySet().stream().filter(k -> !Objects.equals(antes.get(k), despues.get(k))).toList();
        if (!cambios.isEmpty()) {
            predios.saveAndFlush(p);
            auditoria.exito(OperacionAuditoria.MODIFICAR_PREDIO, ENTIDAD, id, Map.of("camposModificados", cambios));
        }
        return vista(p);
    }

    @Transactional(readOnly = true)
    public Page<PredioVista> buscar(String texto, Long municipioId, Pageable pageable) {
        String patron = Textos.vacio(texto) ? null : "%" + texto.trim().toUpperCase(Locale.ROOT) + "%";
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        Map<Long, String> sectores = catalogo.nombresSector();
        return predios.buscar(municipioId, patron, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(p -> vista(p, municipios, sectores));
    }

    @Transactional(readOnly = true)
    public PredioDetalle detalle(Long id) {
        Predio p = obtener(id);
        return new PredioDetalle(vista(p), medidores.dePredio(id), contratos.dePredio(id));
    }

    @Transactional(readOnly = true)
    public List<PredioVista> delUsuario(Long usuarioId) {
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        Map<Long, String> sectores = catalogo.nombresSector();
        return predios.delUsuario(usuarioId).stream().map(p -> vista(p, municipios, sectores)).toList();
    }

    Predio obtener(Long id) {
        return predios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Predio", id));
    }

    private void validar(PredioDatos req, Predio actual) {
        if (actual == null || !Objects.equals(actual.getMunicipioId(), req.municipioId())) {
            catalogo.exigirMunicipioActivo(req.municipioId());
        }
        if (req.sectorId() != null && (actual == null || !Objects.equals(actual.getSectorId(), req.sectorId())
                || !Objects.equals(actual.getMunicipioId(), req.municipioId()))) {
            catalogo.exigirSectorDelMunicipio(req.sectorId(), req.municipioId());
        }
        String ficha = Textos.opcional(req.fichaCatastral());
        if (ficha != null && (actual == null ? predios.existsByFichaCatastral(ficha)
                : predios.existsByFichaCatastralAndIdNot(ficha, actual.getId()))) {
            throw new ConflictoException("DUPLICADO", "Ya existe un predio con esa ficha catastral");
        }
    }

    private static void aplicar(Predio p, PredioDatos req) {
        p.setFichaCatastral(Textos.opcional(req.fichaCatastral()));
        p.setDireccion(Normalizador.texto(req.direccion()));
        p.setBarrioVereda(Textos.opcional(req.barrioVereda()));
        p.setMunicipioId(req.municipioId());
        p.setSectorId(req.sectorId());
        p.setUso(req.uso());
        p.setEstrato(req.uso() == UsoPredio.RESIDENCIAL ? req.estrato() : null);
    }

    private static Map<String, Object> foto(Predio p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fichaCatastral", p.getFichaCatastral());
        m.put("direccion", p.getDireccion());
        m.put("barrioVereda", p.getBarrioVereda());
        m.put("municipioId", p.getMunicipioId());
        m.put("sectorId", p.getSectorId());
        m.put("uso", p.getUso());
        m.put("estrato", p.getEstrato());
        return m;
    }

    PredioVista vista(Predio p) {
        return vista(p, catalogo.nombresMunicipio(), catalogo.nombresSector());
    }

    static PredioVista vista(Predio p, Map<Long, String> municipios, Map<Long, String> sectores) {
        return new PredioVista(p.getId(), p.getCodigoPredio(), p.getFichaCatastral(), p.getDireccion(), p.getBarrioVereda(),
                p.getMunicipioId(), municipios.get(p.getMunicipioId()), p.getSectorId(),
                p.getSectorId() == null ? null : sectores.get(p.getSectorId()), p.getUso(), p.getEstrato(), p.getVersion());
    }
}
