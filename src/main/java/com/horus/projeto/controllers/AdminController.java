package com.horus.projeto.controllers;

import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.PermissaoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Painel Admin (master). Todas as rotas exigem ROLE_MASTER
 * (configurado em SecurityConfigurations: /api/admin/** → hasRole MASTER).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService service;

    private Long getUsuarioLogadoId() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getId();
    }

    /* ── Dashboard ──────────────────────────────────────────────────────── */

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(service.stats());
    }

    /* ── Empresas ───────────────────────────────────────────────────────── */

    @GetMapping("/empresas")
    public ResponseEntity<List<EmpresaEntity>> listarEmpresas() {
        return ResponseEntity.ok(service.listarEmpresas());
    }

    @PostMapping("/empresas")
    public ResponseEntity<?> salvarEmpresa(@RequestBody EmpresaEntity empresa) {
        try {
            return ResponseEntity.ok(service.salvarEmpresa(empresa));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/empresas/{id}/ativo")
    public ResponseEntity<?> alternarAtivoEmpresa(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.alternarAtivoEmpresa(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/empresas/{id}/logo")
    public ResponseEntity<?> uploadLogo(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) {
        try {
            service.uploadLogo(id, arquivo);
            return ResponseEntity.ok(Map.of("mensagem", "Logo atualizada com sucesso."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao processar a imagem."));
        }
    }

    @DeleteMapping("/empresas/{id}/logo")
    public ResponseEntity<?> removerLogo(@PathVariable Long id) {
        try {
            service.removerLogo(id);
            return ResponseEntity.ok(Map.of("mensagem", "Logo removida."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/empresas/{id}/logo")
    public ResponseEntity<byte[]> verLogo(@PathVariable Long id) {
        try {
            EmpresaEntity empresa = service.buscarEmpresa(id);
            if (!empresa.isPossuiLogo()) return ResponseEntity.notFound().build();
            String tipo = empresa.getLogoContentType() != null ? empresa.getLogoContentType() : "image/png";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, tipo)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(empresa.getLogo());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /* ── Usuários ───────────────────────────────────────────────────────── */

    @GetMapping("/usuarios")
    public ResponseEntity<List<Map<String, Object>>> listarUsuarios(@RequestParam(required = false) Long empresaId) {
        return ResponseEntity.ok(service.listarUsuarios(empresaId));
    }

    public record NovoUsuarioRequest(String nome, String login, String senha, String perfil, Long empresaId) {}

    @PostMapping("/usuarios")
    public ResponseEntity<?> criarUsuario(@RequestBody NovoUsuarioRequest req) {
        try {
            return ResponseEntity.ok(service.criarUsuario(req.nome(), req.login(), req.senha(), req.perfil(), req.empresaId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    public record EditarUsuarioRequest(String nome, String perfil, Long empresaId) {}

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<?> atualizarUsuario(@PathVariable Long id, @RequestBody EditarUsuarioRequest req) {
        try {
            return ResponseEntity.ok(service.atualizarUsuario(id, req.nome(), req.perfil(), req.empresaId(), getUsuarioLogadoId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/usuarios/{id}/ativo")
    public ResponseEntity<?> alternarAtivoUsuario(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.alternarAtivoUsuario(id, getUsuarioLogadoId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/usuarios/{id}/reset-senha")
    public ResponseEntity<?> resetarSenha(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("novaSenha", service.resetarSenha(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /* ── Permissões ─────────────────────────────────────────────────────── */

    @GetMapping("/permissoes")
    public ResponseEntity<List<PermissaoEntity>> catalogoPermissoes() {
        return ResponseEntity.ok(service.catalogoPermissoes());
    }

    @GetMapping("/usuarios/{id}/permissoes")
    public ResponseEntity<?> permissoesDoUsuario(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.permissoesDoUsuario(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    public record PermissoesRequest(List<String> permissoes) {}

    @PutMapping("/usuarios/{id}/permissoes")
    public ResponseEntity<?> salvarPermissoes(@PathVariable Long id, @RequestBody PermissoesRequest req) {
        try {
            return ResponseEntity.ok(service.salvarPermissoesUsuario(id, req.permissoes()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
