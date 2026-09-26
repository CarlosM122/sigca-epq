package com.epq.sigca.usuario.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.epq.sigca.usuario.dto.RegistroUsuarioRequest;
import com.epq.sigca.usuario.dto.TipoDocumentoResponse;
import com.epq.sigca.usuario.dto.UsuarioResponse;
import com.epq.sigca.usuario.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RegistroUsuarioController {

    private final UsuarioService usuarioService;

    public RegistroUsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

   
    @GetMapping("/tipos-documento")
    public List<TipoDocumentoResponse> tiposDocumento() {
        return usuarioService.listarTiposDocumento();
    }

   
    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistroUsuarioRequest request) {
        return usuarioService.registrar(request);
    }
}
