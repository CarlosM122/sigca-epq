package com.epq.sigca.usuario.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tabla USUARIO. Los nombres de columna salen del nombre del campo
 * (numeroDocumento -> numero_documento).
 * La columna FECHA_REGISTRO no se mapea: la llena la base de datos (DEFAULT SYSTIMESTAMP).
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // columna GENERATED ALWAYS AS IDENTITY
    private Long id;

    @Enumerated(EnumType.STRING)
    private TipoPersona tipoPersona;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_documento_id")
    private TipoDocumento tipoDocumento;

    private String numeroDocumento;

    private String nombres;

    private String apellidos;

    private String razonSocial;

    private String email;

    private String telefono;

    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estado_usuario_id")
    private EstadoUsuario estado;

    private OffsetDateTime fechaValidacionIdentidad;

    private OffsetDateTime fechaAceptaTratDatos;

    public Long getId() { return id; }

    public TipoPersona getTipoPersona() { return tipoPersona; }
    public void setTipoPersona(TipoPersona tipoPersona) { this.tipoPersona = tipoPersona; }

    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(TipoDocumento tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public EstadoUsuario getEstado() { return estado; }
    public void setEstado(EstadoUsuario estado) { this.estado = estado; }

    public OffsetDateTime getFechaValidacionIdentidad() { return fechaValidacionIdentidad; }
    public void setFechaValidacionIdentidad(OffsetDateTime fecha) { this.fechaValidacionIdentidad = fecha; }

    public OffsetDateTime getFechaAceptaTratDatos() { return fechaAceptaTratDatos; }
    public void setFechaAceptaTratDatos(OffsetDateTime fecha) { this.fechaAceptaTratDatos = fecha; }
}
