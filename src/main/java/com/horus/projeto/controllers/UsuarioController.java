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
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * Sessão atual. Só responde 200 se o token ainda for válido — o frontend usa
     * isto no boot para decidir entre abrir o workspace ou a tela de login.
     *
     * Antes disso o frontend só olhava se HAVIA token no localStorage, nunca se
     * ele ainda valia: o app abria no workspace com um token expirado, mostrando
     * o último usuário e sem conseguir carregar nada.
     *
     * Devolve os dados de identificação do SERVIDOR, não do localStorage, então
     * nome de usuário e empresa nunca ficam defasados.
     */
    @GetMapping("/api/auth/me")
    public ResponseEntity<?> sessaoAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioEntity usuario)) {
            return ResponseEntity.status(401).body(java.util.Map.of("erro", "Sessão inválida."));
        }

        String nome = (usuario.getNome() != null && !usuario.getNome().isBlank())
                ? usuario.getNome() : usuario.getLogin();
        String empresaNome = "Horus Workspace";
        Long empresaId = null;
        if (usuario.getEmpresa() != null) {
            empresaId = usuario.getEmpresa().getId();
            if (usuario.getEmpresa().getRazaoSocial() != null)
                empresaNome = usuario.getEmpresa().getRazaoSocial();
        }

        var resposta = new java.util.LinkedHashMap<String, Object>();
        resposta.put("login", usuario.getLogin());
        resposta.put("nome", nome);
        resposta.put("perfil", usuario.getPerfil());
        resposta.put("empresaId", empresaId);
        resposta.put("empresaNome", empresaNome);
        resposta.put("permissoes", usuario.getPermissoes() == null ? java.util.List.of()
                : usuario.getPermissoes().stream()
                    .map(com.horus.projeto.entities.PermissaoEntity::getNome).sorted().toList());
        return ResponseEntity.ok(resposta);
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