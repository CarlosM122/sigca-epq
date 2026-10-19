package co.gov.epq.sigca.contratos;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.contratos.ContratoDtos.MedidorInstalar;
import co.gov.epq.sigca.contratos.ContratoDtos.MedidorRetiro;
import co.gov.epq.sigca.contratos.ContratoDtos.MedidorVista;
import co.gov.epq.sigca.usuarios.PredioRepository;
import co.gov.epq.sigca.usuarios.Textos;
import co.gov.epq.sigca.usuarios.Versiones;

/**
 * RF-18 medidores (7.3, P-06): solo acueducto y gas; uno activo por predio y servicio. Al instalar el reemplazo
 * de un medidor retirado, el contrato vigente de ese servicio pasa a usar el nuevo (reposición).
 */
@Service
public class MedidorService {

    static final String ENTIDAD = "MEDIDOR";

    private final MedidorRepository medidores;
    private final ContratoServicioRepository contratos;
    private final PredioRepository predios;
    private final Auditoria auditoria;

    public MedidorService(MedidorRepository medidores, ContratoServicioRepository contratos, PredioRepository predios,
            Auditoria auditoria) {
        this.medidores = medidores;
        this.contratos = contratos;
        this.predios = predios;
        this.auditoria = auditoria;
    }

    @Transactional
    public MedidorVista instalar(Long predioId, MedidorInstalar req) {
        if (!predios.existsById(predioId)) {
            throw new RecursoNoEncontradoException("Predio", predioId);
        }
        if (req.tipoServicio() == TipoServicio.ALCANTARILLADO) {
            throw new NegocioException("VALIDACION",
                    "El alcantarillado no lleva medidor: se liquida con el consumo del acueducto", HttpStatus.BAD_REQUEST);
        }
        String serial = req.serial().trim().toUpperCase(Locale.ROOT);
        if (medidores.existsBySerial(serial)) {
            throw new ConflictoException("DUPLICADO", "Ya existe un medidor con ese serial");
        }
        medidores.findFirstByPredioIdAndTipoServicioAndEstado(predioId, req.tipoServicio(), EstadoMedidor.ACTIVO).ifPresent(m -> {
            throw new ConflictoException("DUPLICADO",
                    "El predio ya tiene el medidor activo " + m.getSerial() + " para ese servicio; retírelo primero");
        });
        Medidor m = new Medidor();
        m.setSerial(serial);
        m.setPredioId(predioId);
        m.setTipoServicio(req.tipoServicio());
        m.setMarca(Textos.opcional(req.marca()));
        m.setFechaInstalacion(req.fechaInstalacion());
        m.setLecturaInicial(req.lecturaInicial());
        m.setEstado(EstadoMedidor.ACTIVO);
        medidores.saveAndFlush(m);
        auditoria.exito(OperacionAuditoria.CREAR_MEDIDOR, ENTIDAD, m.getId(),
                Map.of("serial", serial, "predioId", predioId, "tipoServicio", m.getTipoServicio()));

        // Reposición: el contrato vigente del servicio quedó con un medidor retirado
        contratos.findFirstByPredioIdAndTipoServicioAndEstadoIn(predioId, req.tipoServicio(), ContratoService.VIGENTES)
                .filter(c -> c.getMedidorId() != null && !c.getMedidorId().equals(m.getId()))
                .filter(c -> medidores.findById(c.getMedidorId()).map(a -> a.getEstado() == EstadoMedidor.RETIRADO).orElse(false))
                .ifPresent(c -> {
                    Long anterior = c.getMedidorId();
                    c.setMedidorId(m.getId());
                    contratos.saveAndFlush(c);
                    auditoria.exito(OperacionAuditoria.MODIFICAR_CONTRATO, ContratoService.ENTIDAD, c.getId(),
                            Map.of("reposicionMedidor", true, "medidorAnteriorId", anterior, "medidorNuevoId", m.getId()));
                });
        return vista(m);
    }

    @Transactional
    public MedidorVista retirar(Long id, MedidorRetiro req) {
        Medidor m = obtener(id);
        Versiones.exigir(req.version(), m.getVersion());
        if (m.getEstado() == EstadoMedidor.RETIRADO) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "El medidor ya está retirado");
        }
        if (req.fechaRetiro().isBefore(m.getFechaInstalacion())) {
            throw new NegocioException("VALIDACION", "La fecha de retiro no puede ser anterior a la instalación", HttpStatus.BAD_REQUEST);
        }
        if (Boolean.TRUE.equals(m.getLecturaEnRevision())) {
            throw new ConflictoException("LECTURA_EN_REVISION", "El medidor tiene una lectura en revisión: revísela antes de retirarlo");
        }
        m.setEstado(EstadoMedidor.RETIRADO);
        m.setFechaRetiro(req.fechaRetiro());
        medidores.saveAndFlush(m);
        Map<String, Object> detalle = new HashMap<>(Map.of("serial", m.getSerial(), "fechaRetiro", req.fechaRetiro().toString()));
        contratos.findFirstByMedidorIdAndEstadoIn(id, ContratoService.VIGENTES)
                .ifPresent(c -> detalle.put("contratoPendienteReposicion", c.getNumeroContrato()));
        auditoria.exito(OperacionAuditoria.RETIRAR_MEDIDOR, ENTIDAD, id, detalle);
        return vista(m);
    }

    @Transactional(readOnly = true)
    public List<MedidorVista> dePredio(Long predioId) {
        List<Medidor> lista = medidores.findByPredioIdOrderByEstadoAscFechaInstalacionDesc(predioId);
        Map<Long, String> numeros = new HashMap<>();
        if (!lista.isEmpty()) {
            contratos.findByMedidorIdInAndEstadoIn(lista.stream().map(Medidor::getId).toList(), ContratoService.VIGENTES)
                    .forEach(c -> numeros.put(c.getMedidorId(), c.getNumeroContrato()));
        }
        return lista.stream().map(m -> vista(m, numeros.get(m.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public MedidorVista consultar(Long id) {
        return vista(obtener(id));
    }

    Medidor obtener(Long id) {
        return medidores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Medidor", id));
    }

    private MedidorVista vista(Medidor m) {
        String numero = contratos.findFirstByMedidorIdAndEstadoIn(m.getId(), ContratoService.VIGENTES)
                .map(ContratoServicio::getNumeroContrato).orElse(null);
        return vista(m, numero);
    }

    private static MedidorVista vista(Medidor m, String numeroContrato) {
        return new MedidorVista(m.getId(), m.getSerial(), m.getPredioId(), m.getTipoServicio(), m.getMarca(),
                m.getFechaInstalacion(), m.getLecturaInicial(), m.getLecturaActual(), m.getUltimoPeriodo(),
                Boolean.TRUE.equals(m.getLecturaEnRevision()), m.getEstado(), m.getFechaRetiro(), numeroContrato, m.getVersion());
    }
}
