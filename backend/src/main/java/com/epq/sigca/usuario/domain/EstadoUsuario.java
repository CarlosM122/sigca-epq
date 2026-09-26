package com.epq.sigca.usuario.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo (solo lectura): tabla ESTADO_USUARIO. */
@Entity
@Table(name = "estado_usuario")
public class EstadoUsuario {

    @Id
    private Integer id;

    private String codigo;

    private String nombre;

    protected EstadoUsuario() {
        // requerido por JPA
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
}
