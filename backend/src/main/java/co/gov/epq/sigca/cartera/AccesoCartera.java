package co.gov.epq.sigca.cartera;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.seguridad.UsuarioActual;

/**
 * Alcance territorial de cartera: un funcionario con municipio asignado solo ve y gestiona usuarios con predios en
 * su municipio; los funcionarios sin municipio (Gestor de Cartera central, Gerente) ven todos.
 */
@Component
public class AccesoCartera {

    private final CarteraConsultas consultas;

    public AccesoCartera(CarteraConsultas consultas) {
        this.consultas = consultas;
    }

    /** Municipio al que se debe restringir la consulta, o el pedido si el funcionario no tiene restricción. */
    Long municipioEfectivo(Long pedido) {
        Long propio = UsuarioActual.funcionario().municipioId();
        if (propio == null) {
            return pedido;
        }
        if (pedido != null && !pedido.equals(propio)) {
            throw new NegocioException("ACCESO_DENEGADO", "Solo puede consultar la cartera de su municipio", HttpStatus.FORBIDDEN);
        }
        return propio;
    }

    CarteraConsultas.UsuarioCartera usuario(Long usuarioId) {
        CarteraConsultas.UsuarioCartera u = consultas.usuario(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));
        Long propio = UsuarioActual.funcionario().municipioId();
        if (propio != null && !consultas.municipiosDeUsuario(usuarioId).contains(propio)) {
            throw new NegocioException("ACCESO_DENEGADO", "El usuario no tiene predios en su municipio", HttpStatus.FORBIDDEN);
        }
        return u;
    }
}
