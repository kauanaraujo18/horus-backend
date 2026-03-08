package com.horus.projeto.controllers; // Ajuste para a sua pasta de controllers

import com.horus.projeto.dto.LoginRequestDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/login")
@CrossOrigin("*") // Permite o acesso do Frontend
public class AutenticacaoController {

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    @PostMapping
    public ResponseEntity<?> efetuarLogin(@RequestBody LoginRequestDTO dados) {
        try {
            var authenticationToken = new UsernamePasswordAuthenticationToken(dados.getLogin(), dados.getSenha());
            var authentication = manager.authenticate(authenticationToken);
            
            var usuario = (UsuarioEntity) authentication.getPrincipal();
            var tokenJWT = tokenService.gerarToken(usuario);
            
            return ResponseEntity.ok(new TokenResponse(tokenJWT));
            
        } catch (Exception e) {
            // Isso vai forçar o Java a cuspir o erro real no console e no Postman!
            e.printStackTrace(); 
            return ResponseEntity.badRequest().body("Falha na autenticação: " + e.getMessage());
        }
    }
    
    // Classe auxiliar interna apenas para devolver o token no formato JSON: {"token": "ey..."}
    private record TokenResponse(String token) {}
}