package co.gov.epq.sigca.auditoria;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.seguridad.cuentas.CuentasUsuarioRepository;
import co.gov.epq.sigca.seguridad.cuentas.Funcionario;
import co.gov.epq.sigca.seguridad.cuentas.FuncionarioRepository;

/**
 * Consulta del log de auditoría (RF-15, CU-11). Solo lectura: la tabla es inmutable (TRG_LOG_AUDITORIA_BUD).
 */
@Service
@Transactional(readOnly = true)
public class ConsultaAuditoriaService {

    static final int MAX_DIAS = 366;

    private final LogAuditoriaRepository repo;
    private final FuncionarioRepository funcionarios;
    private final CuentasUsuarioRepository usuarios;

    public ConsultaAuditoriaService(LogAuditoriaRepository repo, FuncionarioRepository funcionarios,
            CuentasUsuarioRepository usuarios) {
        this.repo = repo;
        this.funcionarios = funcionarios;
        this.usuarios = usuarios;
    }

    /**
     * @param username filtra por el funcionario que actuó; si no existe, la consulta queda vacía
     * @param desde    inclusive; por defecto hace 30 días
     * @param hasta    inclusive; por defecto hoy
     */
    public Page<RegistroAuditoria> buscar(OperacionAuditoria operacion, String entidad, Long entidadId, String username,
            Long usuarioId, ResultadoAuditoria resultado, LocalDate desde, LocalDate hasta, Pageable pageable) {
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        LocalDate d = desde == null ? h.minusDays(30) : desde;
        if (d.isAfter(h)) {
            throw new NegocioException("RANGO_INVALIDO", "La fecha inicial no puede ser posterior a la final");
        }
        if (d.plusDays(MAX_DIAS).isBefore(h)) {
            throw new NegocioException("RANGO_INVALIDO", "Consulte como máximo un año a la vez");
        }
        Long funcionarioId = null;
        if (username != null && !username.isBlank()) {
            var f = funcionarios.findByUsername(username.trim().toLowerCase());
            if (f.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            funcionarioId = f.get().getId();
        }
        String ent = entidad == null || entidad.isBlank() ? null : entidad.trim().toUpperCase();
        Page<LogAuditoria> pagina = repo.buscar(operacion, ent, entidadId, funcionarioId, usuarioId, resultado,
                d.atStartOfDay(), h.plusDays(1).atStartOfDay(), pageable);
        Map<Long, String> nombresFuncionario = new HashMap<>();
        List<Long> fids = pagina.stream().map(LogAuditoria::getFuncionarioId).filter(Objects::nonNull).distinct().toList();
        if (!fids.isEmpty()) {
            for (Funcionario f : funcionarios.findAllById(fids)) {
                nombresFuncionario.put(f.getId(), f.getUsername());
            }
        }
        Map<Long, String> codigosUsuario = new HashMap<>();
        List<Long> uids = pagina.stream().map(LogAuditoria::getUsuarioId).filter(Objects::nonNull).distinct().toList();
        if (!uids.isEmpty()) {
            for (Object[] fila : usuarios.codigos(uids)) {
                codigosUsuario.put((Long) fila[0], (String) fila[1]);
            }
        }
        return pagina.map(l -> new RegistroAuditoria(l.getId(), l.getFecha(), l.getActorTipo(), actor(l, nombresFuncionario, codigosUsuario),
                l.getFuncionarioId(), l.getUsuarioId(), l.getOperacion(), l.getEntidad(), l.getEntidadId(), l.getResultado(),
                l.getDetalle(), l.getIpOrigen()));
    }

    private static String actor(LogAuditoria l, Map<Long, String> funcionarios, Map<Long, String> usuarios) {
        return switch (l.getActorTipo()) {
            case FUNCIONARIO -> funcionarios.getOrDefault(l.getFuncionarioId(), "#" + l.getFuncionarioId());
            case CIUDADANO -> usuarios.getOrDefault(l.getUsuarioId(), "#" + l.getUsuarioId());
            case SISTEMA -> "SISTEMA";
            case ANONIMO -> "ANÓNIMO";
        };
    }

    public record RegistroAuditoria(Long id, LocalDateTime fecha, ActorTipo actorTipo, String actor, Long funcionarioId,
            Long usuarioId, OperacionAuditoria operacion, String entidad, Long entidadId, ResultadoAuditoria resultado,
            String detalle, String ipOrigen) {
    }
}
