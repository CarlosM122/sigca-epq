package co.gov.epq.sigca.usuarios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.seguridad.cuentas.EstadoCuentaPortal;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByTipoIdentificacionAndNumeroIdentificacionHash(TipoIdentificacion tipo, String hash);

    @Query("select c.estado from CuentaPortal c where c.usuarioId = :usuarioId")
    Optional<EstadoCuentaPortal> estadoCuentaPortal(Long usuarioId);

    List<Usuario> findByEstadoVerificacionRnecAndActivoTrueOrderById(EstadoVerificacionRnec estado, Pageable pageable);
}
