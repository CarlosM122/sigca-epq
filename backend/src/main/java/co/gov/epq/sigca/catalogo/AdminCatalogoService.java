package co.gov.epq.sigca.catalogo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.validation.Normalizador;

/**
 * Parámetros del sistema, festivos, municipios y sectores (RF-29, P-02, P-09). Los rangos legales los valida
 * TRG_PARAMETRO_CIU al guardar; aquí se revisa el formato según el tipo de dato para dar un mensaje claro.
 */
@Service
@Transactional
public class AdminCatalogoService {

    static final Set<String> MODOS_OPERACION = Set.of("NORMAL", "MANTENIMIENTO", "CONTINGENCIA");

    private final ParametroSistemaRepository parametros;
    private final FestivoRepository festivos;
    private final MunicipioRepository municipios;
    private final SectorRepository sectores;
    private final Auditoria auditoria;

    public AdminCatalogoService(ParametroSistemaRepository parametros, FestivoRepository festivos,
            MunicipioRepository municipios, SectorRepository sectores, Auditoria auditoria) {
        this.parametros = parametros;
        this.festivos = festivos;
        this.municipios = municipios;
        this.sectores = sectores;
        this.auditoria = auditoria;
    }

    // ---------------------------------------------------------------- parámetros

    @Transactional(readOnly = true)
    public List<ParametroVista> parametros() {
        return parametros.findAllByOrderByClave().stream().map(AdminCatalogoService::vista).toList();
    }

    public ParametroVista modificarParametro(String clave, String valor) {
        ParametroSistema p = parametros.findById(clave).orElseThrow(() -> new RecursoNoEncontradoException("Parámetro", clave));
        String nuevo = valor.trim();
        validarFormato(p, nuevo);
        String anterior = p.getValor();
        if (anterior.equals(nuevo)) {
            return vista(p);
        }
        p.setValor(nuevo);
        // El trigger valida los rangos al terminar la sentencia: el error sale aquí y no al confirmar
        parametros.saveAndFlush(p);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clave", clave);
        detalle.put("anterior", anterior);
        detalle.put("nuevo", nuevo);
        auditoria.exito(OperacionAuditoria.MODIFICAR_PARAMETRO, "PARAMETRO_SISTEMA", null, detalle);
        return vista(p);
    }

    /** Formato por tipo de dato, el mismo de CK_PARAMETRO_ENTERO/DECIMAL/BOOLEANO. */
    static void validarFormato(ParametroSistema p, String valor) {
        boolean ok = switch (p.getTipoDato()) {
            case ENTERO -> valor.matches("^-?\\d{1,9}$");
            case DECIMAL -> valor.matches("^-?\\d{1,9}(\\.\\d{1,4})?$");
            case BOOLEANO -> valor.equals("S") || valor.equals("N");
            case TEXTO -> !valor.isEmpty() && valor.length() <= 400 && Normalizador.sinHtml(valor);
        };
        if (!ok) {
            throw new NegocioException("PARAMETRO_INVALIDO", switch (p.getTipoDato()) {
                case ENTERO -> "El parámetro " + p.getClave() + " debe ser un número entero";
                case DECIMAL -> "El parámetro " + p.getClave() + " debe ser un número con punto decimal (máximo 4 decimales)";
                case BOOLEANO -> "El parámetro " + p.getClave() + " debe ser S o N";
                case TEXTO -> "El parámetro " + p.getClave() + " debe tener entre 1 y 400 caracteres, sin HTML";
            });
        }
        if ("MODO_OPERACION".equals(p.getClave()) && !MODOS_OPERACION.contains(valor)) {
            throw new NegocioException("PARAMETRO_INVALIDO", "MODO_OPERACION debe ser NORMAL, MANTENIMIENTO o CONTINGENCIA");
        }
    }

    private static ParametroVista vista(ParametroSistema p) {
        return new ParametroVista(p.getClave(), p.getValor(), p.getTipoDato(), p.getDescripcion(), p.getActualizadoEn(),
                p.getActualizadoPor());
    }

    // ---------------------------------------------------------------- festivos

    @Transactional(readOnly = true)
    public List<FestivoVista> festivos(int anio) {
        return festivos.findByFechaBetweenOrderByFecha(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31)).stream()
                .map(f -> new FestivoVista(f.getFecha(), f.getDescripcion()))
                .toList();
    }

    public FestivoVista agregarFestivo(LocalDate fecha, String descripcion) {
        if (festivos.existsById(fecha)) {
            throw new ConflictoException("DUPLICADO", "La fecha " + fecha + " ya está registrada como festivo");
        }
        Festivo f = new Festivo();
        f.setFecha(fecha);
        f.setDescripcion(Normalizador.texto(descripcion));
        festivos.saveAndFlush(f);
        auditoria.exito(OperacionAuditoria.MODIFICAR_FESTIVOS, "FESTIVO", null,
                Map.of("accion", "AGREGAR", "fecha", fecha.toString(), "descripcion", f.getDescripcion()));
        return new FestivoVista(f.getFecha(), f.getDescripcion());
    }

    /** Un festivo pasado ya se usó para calcular plazos vigentes (PQR, recursos): no se elimina. */
    public void eliminarFestivo(LocalDate fecha) {
        Festivo f = festivos.findById(fecha).orElseThrow(() -> new RecursoNoEncontradoException("Festivo", fecha));
        if (fecha.isBefore(LocalDate.now())) {
            throw new NegocioException("FESTIVO_PASADO", "No se elimina un festivo pasado: ya se usó para calcular plazos");
        }
        festivos.delete(f);
        festivos.flush();
        auditoria.exito(OperacionAuditoria.MODIFICAR_FESTIVOS, "FESTIVO", null,
                Map.of("accion", "ELIMINAR", "fecha", fecha.toString()));
    }

    // ---------------------------------------------------------------- municipios y sectores

    @Transactional(readOnly = true)
    public List<MunicipioVista> municipios() {
        return municipios.findAllByOrderByNombre().stream().map(AdminCatalogoService::vista).toList();
    }

    public MunicipioVista cambiarEstadoMunicipio(Long id, boolean activo) {
        Municipio m = municipios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Municipio", id));
        if (!Boolean.valueOf(activo).equals(m.getActivo())) {
            m.setActivo(activo);
            municipios.saveAndFlush(m);
            auditoria.exito(OperacionAuditoria.MODIFICAR_MUNICIPIO, "MUNICIPIO", id, Map.of("activo", activo));
        }
        return vista(m);
    }

    @Transactional(readOnly = true)
    public List<SectorVista> sectores(Long municipioId) {
        if (!municipios.existsById(municipioId)) {
            throw new RecursoNoEncontradoException("Municipio", municipioId);
        }
        return sectores.findByMunicipioIdOrderByNombre(municipioId).stream().map(AdminCatalogoService::vista).toList();
    }

    public SectorVista crearSector(Long municipioId, String nombre, TipoSector tipo) {
        if (!municipios.existsById(municipioId)) {
            throw new RecursoNoEncontradoException("Municipio", municipioId);
        }
        Sector s = new Sector();
        s.setMunicipioId(municipioId);
        s.setNombre(Normalizador.texto(nombre));
        s.setTipo(tipo);
        s.setActivo(true);
        sectores.saveAndFlush(s);
        auditoria.exito(OperacionAuditoria.CREAR_SECTOR, "SECTOR", s.getId(),
                Map.of("municipioId", municipioId, "nombre", s.getNombre(), "tipo", tipo.name()));
        return vista(s);
    }

    public SectorVista modificarSector(Long id, String nombre, TipoSector tipo) {
        Sector s = sector(id);
        String n = Normalizador.texto(nombre);
        if (!n.equals(s.getNombre()) || tipo != s.getTipo()) {
            s.setNombre(n);
            s.setTipo(tipo);
            sectores.saveAndFlush(s);
            auditoria.exito(OperacionAuditoria.MODIFICAR_SECTOR, "SECTOR", id, Map.of("nombre", n, "tipo", tipo.name()));
        }
        return vista(s);
    }

    public SectorVista cambiarEstadoSector(Long id, boolean activo) {
        Sector s = sector(id);
        if (!Boolean.valueOf(activo).equals(s.getActivo())) {
            s.setActivo(activo);
            sectores.saveAndFlush(s);
            auditoria.exito(OperacionAuditoria.MODIFICAR_SECTOR, "SECTOR", id, Map.of("activo", activo));
        }
        return vista(s);
    }

    private Sector sector(Long id) {
        return sectores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Sector", id));
    }

    private static MunicipioVista vista(Municipio m) {
        return new MunicipioVista(m.getId(), m.getCodigoDane(), m.getNombre(), Boolean.TRUE.equals(m.getActivo()));
    }

    private static SectorVista vista(Sector s) {
        return new SectorVista(s.getId(), s.getMunicipioId(), s.getNombre(), s.getTipo(), Boolean.TRUE.equals(s.getActivo()));
    }

    public record ParametroVista(String clave, String valor, TipoDato tipoDato, String descripcion,
            LocalDateTime actualizadoEn, String actualizadoPor) {
    }

    public record FestivoVista(LocalDate fecha, String descripcion) {
    }

    public record MunicipioVista(Long id, String codigoDane, String nombre, boolean activo) {
    }

    public record SectorVista(Long id, Long municipioId, String nombre, TipoSector tipo, boolean activo) {
    }
}
