package com.epq.sigca.usuario.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.epq.sigca.usuario.domain.EstadoUsuario;

public interface EstadoUsuarioRepository extends JpaRepository<EstadoUsuario, Integer> {

    Optional<EstadoUsuario> findByCodigo(String codigo);
}
