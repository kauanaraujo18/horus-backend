package com.horus.projeto.controllers;

import com.horus.projeto.dto.LoginRequestDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*") // Libera acesso do Front
public class UsuarioController {

    // 1. Injetamos o motor oficial de autenticação (que usa o BCrypt!)
    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    // 2. A MÁGICA: O endpoint exato que o seu JS está chamando
    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginData) {
        try {
            // O manager pega a senha real, criptografa e compara com o banco de dados
            var authenticationToken = new UsernamePasswordAuthenticationToken(loginData.getLogin(), loginData.getSenha());
            var authentication = manager.authenticate(authenticationToken);
            
            // Se a senha estiver correta, gera e devolve o Token
            var usuario = (UsuarioEntity) authentication.getPrincipal();
            var tokenJWT = tokenService.gerarToken(usuario);
            
            return ResponseEntity.ok(new TokenResponse(tokenJWT));
            
        } catch (Exception e) {
            System.out.println("Falha de autenticação: " + e.getMessage());
            // Retorna 401 Unauthorized para o Front exibir a mensagem de "usuário ou senha incorretos"
            return ResponseEntity.status(401).body("Usuário ou senha incorretos.");
        }
    }

    // Se no futuro você criar métodos para CADASTRAR ou LISTAR usuários, 
    // basta criar os métodos aqui com a rota específica, por exemplo:
    // @PostMapping("/api/usuarios")
    // public ResponseEntity<?> salvarUsuario(...) { ... }

    // Classe auxiliar para devolver o token no padrão exato que o seu JS espera {"token": "ey..."}
    private record TokenResponse(String token) {}
}