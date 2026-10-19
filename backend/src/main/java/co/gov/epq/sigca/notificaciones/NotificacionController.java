package co.gov.epq.sigca.notificaciones;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.seguridad.UsuarioActual;

/**
 * Bandeja interna del funcionario autenticado (alertas de PQR, lecturas en revisión, órdenes, etc.).
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionRepository repo;

    public NotificacionController(NotificacionRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificacionView> mias() {
        Long fid = UsuarioActual.funcionario().funcionarioId();
        return repo.findByCanalAndFuncionarioIdOrderByIdDesc(CanalNotificacion.SISTEMA, fid, PageRequest.of(0, 50))
                .stream().map(NotificacionView::de).toList();
    }

    @GetMapping("/no-leidas")
    @Transactional(readOnly = true)
    public long noLeidas() {
        return repo.countByCanalAndFuncionarioIdAndLeidaFalse(CanalNotificacion.SISTEMA, UsuarioActual.funcionario().funcionarioId());
    }

    @PostMapping("/{id}/leida")
    @Transactional
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        Long fid = UsuarioActual.funcionario().funcionarioId();
        Notificacion n = repo.findById(id)
                .filter(x -> x.getCanal() == CanalNotificacion.SISTEMA && fid.equals(x.getFuncionarioId()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Notificación", id));
        n.setLeida(true);
        return ResponseEntity.noContent().build();
    }

    public record NotificacionView(Long id, EventoNotificacion evento, String asunto, String cuerpo, String entidad,
            Long entidadId, LocalDateTime creadaEn, boolean leida) {

        static NotificacionView de(Notificacion n) {
            return new NotificacionView(n.getId(), n.getEvento(), n.getAsunto(), n.getCuerpo(), n.getEntidad(),
                    n.getEntidadId(), n.getCreadoEn(), Boolean.TRUE.equals(n.getLeida()));
        }
    }
}
