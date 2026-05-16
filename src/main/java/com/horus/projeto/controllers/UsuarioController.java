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

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginData) {
        try {
            // 1. O manager valida o login e a senha no banco de dados
            var authenticationToken = new UsernamePasswordAuthenticationToken(loginData.getLogin(), loginData.getSenha());
            var authentication = manager.authenticate(authenticationToken);
            
            // 2. Se a senha estiver correta, gera o Token
            var usuario = (UsuarioEntity) authentication.getPrincipal();
            var tokenJWT = tokenService.gerarToken(usuario);
            
            // 3. Valores padrão seguros (caso algo falhe, o login não trava)
            String nomeUsuario = usuario.getLogin(); 
            String empresaNome = "Horus Workspace";

            // Captura isolada do Nome do Usuário
            try {
                if (usuario.getNome() != null && !usuario.getNome().isEmpty()) {
                    nomeUsuario = usuario.getNome();
                }
            } catch (Throwable t) {
                // Se der qualquer erro ao ler o método getNome, ignora e mantém o login
            }

            // Captura isolada do Nome da Empresa (Protege contra LazyInitializationException)
            try {
                if (usuario.getEmpresa() != null) {
                    empresaNome = usuario.getEmpresa().getRazaoSocial();
                }
            } catch (Throwable t) {
                // Se der erro de carregamento da empresa, ignora e mantém o padrão
            }
            
            // 4. Retorna a resposta com os 3 dados que o Front-end precisa
            return ResponseEntity.ok(new TokenResponse(tokenJWT, nomeUsuario, empresaNome));

        } catch (Exception e) {
            System.out.println("Falha de autenticação real (Senha incorreta): " + e.getMessage());
            return ResponseEntity.status(401).body("Usuário ou senha incorretos.");
        }
    }

    // Record atualizado para carregar os dados que o seu plug do main.js espera receber
    private record TokenResponse(String token, String nome, String empresaNome) {}
}