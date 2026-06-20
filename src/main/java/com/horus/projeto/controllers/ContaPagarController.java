package com.horus.projeto.controllers;

import com.horus.projeto.dto.ContaPagarRequestDTO;
import com.horus.projeto.entities.ContaPagarEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.ContaPagarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contas-pagar")
@RequiredArgsConstructor
public class ContaPagarController {

    private final ContaPagarService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    /* ──────────────────── LISTAR ──────────────────── */

    @GetMapping
    public ResponseEntity<List<ContaPagarEntity>> listar(
            @RequestParam(required = false) String termo) {
        Long empresaId = getEmpresaIdLogada();
        if (termo != null && !termo.isBlank()) {
            return ResponseEntity.ok(service.pesquisar(termo, empresaId));
        }
        return ResponseEntity.ok(service.listarPorEmpresa(empresaId));
    }

    /* ──────────────────── BUSCAR POR ID ──────────────────── */

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id, getEmpresaIdLogada()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("erro", e.getMessage()));
        }
    }

    /* ──────────────────── SALVAR ──────────────────── */

    @PostMapping
    public ResponseEntity<?> salvar(@RequestBody ContaPagarRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.salvar(dto, getEmpresaIdLogada()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    /* ──────────────────── ATUALIZAR ──────────────────── */

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id,
                                        @RequestBody ContaPagarRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizar(id, dto, getEmpresaIdLogada()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Acesso Negado")) {
                return ResponseEntity.status(403).body(Map.of("erro", e.getMessage()));
            }
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    /* ──────────────────── MARCAR COMO PAGA ──────────────────── */

    public record PagarRequest(Long codContaFinanceira) {}

    @PatchMapping("/{id}/pagar")
    public ResponseEntity<?> marcarComoPaga(@PathVariable Long id, @RequestBody(required = false) PagarRequest req) {
        try {
            Long codConta = req != null ? req.codContaFinanceira() : null;
            return ResponseEntity.ok(service.marcarComoPaga(id, getEmpresaIdLogada(), codConta));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("erro", e.getMessage()));
        }
    }

    /* ──────────────────── DELETAR ──────────────────── */

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        try {
            service.deletar(id, getEmpresaIdLogada());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("erro", e.getMessage()));
        }
    }
}
