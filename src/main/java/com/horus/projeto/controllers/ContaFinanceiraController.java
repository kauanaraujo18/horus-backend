package com.horus.projeto.controllers;

import com.horus.projeto.dto.ContaFinanceiraRequestDTO;
import com.horus.projeto.entities.ContaFinanceiraEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.ContaFinanceiraService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/financeiro/contas")
@RequiredArgsConstructor
public class ContaFinanceiraController {

    private final ContaFinanceiraService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    @GetMapping
    public ResponseEntity<List<ContaFinanceiraEntity>> listar() {
        return ResponseEntity.ok(service.listar(getEmpresaIdLogada()));
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody ContaFinanceiraRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.criar(getEmpresaIdLogada(), dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody ContaFinanceiraRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizar(getEmpresaIdLogada(), id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/ativo")
    public ResponseEntity<?> alternarAtivo(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.alternarAtivo(getEmpresaIdLogada(), id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/padrao")
    public ResponseEntity<?> definirPadrao(@PathVariable Long id) {
        try {
            service.definirPadrao(getEmpresaIdLogada(), id);
            return ResponseEntity.ok(Map.of("mensagem", "Conta definida como padrão."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        try {
            service.deletar(getEmpresaIdLogada(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("erro",
                    "Esta conta está vinculada a movimentações e não pode ser excluída. Inative-a."));
        }
    }
}
