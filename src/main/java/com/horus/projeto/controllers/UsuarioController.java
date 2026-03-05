package com.horus.projeto.controllers;

import com.horus.projeto.dto.LoginRequestDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*") // Libera acesso do Front
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginData) {
        try {
            // Chama o serviço para verificar
            UsuarioEntity usuario = usuarioService.autenticar(loginData.getLogin(), loginData.getSenha());
            
            // Retorna sucesso e os dados do usuário (menos a senha, idealmente, mas ok por agora)
            return ResponseEntity.ok(usuario);
            
        } catch (RuntimeException e) {
            // Retorna erro 401 (Não Autorizado) com a mensagem
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}