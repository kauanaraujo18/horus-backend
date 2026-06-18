package com.horus.projeto.controllers;

import com.horus.projeto.dto.LoginRequestDTO;
import com.horus.projeto.dto.RegistroRequestDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.repositories.UsuarioRepository;
import com.horus.projeto.services.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class UsuarioController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UsuarioController.class);

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository repository;

    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginData) {
        try {
            var authenticationToken = new UsernamePasswordAuthenticationToken(loginData.getLogin(), loginData.getSenha());
            var authentication = manager.authenticate(authenticationToken);
            
            var usuario = (UsuarioEntity) authentication.getPrincipal();
            var tokenJWT = tokenService.gerarToken(usuario);
            
            String nomeUsuario = usuario.getLogin(); 
            String empresaNome = "Horus Workspace";

            try {
                if (usuario.getNome() != null && !usuario.getNome().isEmpty()) {
                    nomeUsuario = usuario.getNome();
                }
            } catch (Throwable t) {}

            try {
                if (usuario.getEmpresa() != null) {
                    empresaNome = usuario.getEmpresa().getRazaoSocial();
                }
            } catch (Throwable t) {}
            
            return ResponseEntity.ok(new TokenResponse(tokenJWT, nomeUsuario, empresaNome, usuario.getPerfil()));

        } catch (Exception e) {
            log.warn("Falha de autenticação para o login informado.");
            return ResponseEntity.status(401).body("Usuário ou senha incorretos.");
        }
    }

    // ========================================================================
    // NOVO ENDPOINT: COFRE DE REGISTO PÚBLICO
    // ========================================================================
    @PostMapping("/api/auth/registro")
    public ResponseEntity<?> registrarConta(@RequestBody RegistroRequestDTO dto) {
        try {
            // 1. Validação de bloqueio rápido: Verifica se o email já existe
            if (repository.findByLogin(dto.getEmailProprietario()).isPresent()) {
                return ResponseEntity.badRequest().body("Este e-mail já se encontra registado no sistema.");
            }

            // 2. Senha provisória (O formulário público não pede senha por motivos de conversão)
            String senhaProvisoria = "Mudar@123";

            // 3. Executa a transação atómica
            repository.registrarNovaConta(
                    dto.getRazaoSocial(),
                    dto.getNomeFantasia(),
                    dto.getCnpj(),
                    dto.getNomeProprietario(),
                    dto.getTelefoneProprietario(),
                    dto.getEmailProprietario(),
                    dto.getCpfProprietario(),
                    dto.getDataNascimentoProprietario(),
                    senhaProvisoria
            );

            // Resposta de Sucesso limpa para o Frontend
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Erro ao registrar nova conta", e);
            return ResponseEntity.internalServerError().body("Ocorreu um erro interno ao processar o registo.");
        }
    }

    private record TokenResponse(String token, String nome, String empresaNome, String perfil) {}
}