package com.epq.sigca.usuario.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.epq.sigca.usuario.domain.TipoDocumento;
import com.epq.sigca.usuario.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);

   
    @Query("select case when count(u) > 0 then true else false end "
            + "from Usuario u where lower(u.email) = lower(:email)")
    boolean existeCorreo(@Param("email") String email);
}
