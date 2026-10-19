package co.gov.epq.sigca.seguridad.cuentas;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.gov.epq.sigca.common.validation.TipoIdentificacion;
import co.gov.epq.sigca.usuarios.Usuario;

/** Lectura de USUARIO para el portal (la entidad es del módulo de usuarios). */
public interface CuentasUsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByTipoIdentificacionAndNumeroIdentificacionHash(TipoIdentificacion tipo, String hash);

    @Query("select u.id, u.codigo from Usuario u where u.id in :ids")
    List<Object[]> codigos(@Param("ids") Collection<Long> ids);
}
