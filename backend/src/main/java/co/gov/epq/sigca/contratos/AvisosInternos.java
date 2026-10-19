package co.gov.epq.sigca.contratos;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.notificaciones.Notificaciones;

/** Avisos en la bandeja interna de los asesores del municipio (lecturas en revisión, reconexiones vencidas). */
@Component
public class AvisosInternos {

    private final ContratosFuncionarioRepository funcionarios;
    private final Notificaciones notificaciones;

    public AvisosInternos(ContratosFuncionarioRepository funcionarios, Notificaciones notificaciones) {
        this.funcionarios = funcionarios;
        this.notificaciones = notificaciones;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int aAsesores(Long municipioId, EventoNotificacion evento, String asunto, String cuerpo, String entidad, Long entidadId) {
        var ids = funcionarios.asesoresDelMunicipio(municipioId);
        ids.forEach(id -> notificaciones.funcionario(evento, id, asunto, cuerpo, entidad, entidadId));
        return ids.size();
    }
}
