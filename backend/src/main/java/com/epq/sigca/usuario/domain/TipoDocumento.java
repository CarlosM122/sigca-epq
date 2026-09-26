package com.epq.sigca.usuario.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo (solo lectura): tabla TIPO_DOCUMENTO. */
@Entity
@Table(name = "tipo_documento")
public class TipoDocumento {

    @Id
    private Integer id;

    private String codigo;

    private String nombre;

    @Enumerated(EnumType.STRING)
    private TipoPersona tipoPersona;

   
    private boolean validaRnec;

    protected TipoDocumento() {
        // requerido por JPA
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public TipoPersona getTipoPersona() { return tipoPersona; }
    public boolean isValidaRnec() { return validaRnec; }
}
