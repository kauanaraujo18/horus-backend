package com.horus.projeto.controllers;

import com.horus.projeto.dto.ClasseFinanceiraRequestDTO;
import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.ClasseFinanceiraService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/financeiro/classes")
@RequiredArgsConstructor
public class ClasseFinanceiraController {

    private final ClasseFinanceiraService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    @GetMapping
    public ResponseEntity<List<ClasseFinanceiraEntity>> listar() {
        return ResponseEntity.ok(service.listar(getEmpresaIdLogada()));
    }

    /** Analíticas ativas — para os seletores de Produto e Conta a Pagar. */
    @GetMapping("/analiticas")
    public ResponseEntity<List<ClasseFinanceiraEntity>> listarAnaliticas() {
        return ResponseEntity.ok(service.listarAnaliticas(getEmpresaIdLogada()));
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody ClasseFinanceiraRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.criar(getEmpresaIdLogada(), dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody ClasseFinanceiraRequestDTO dto) {
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        try {
            service.deletar(getEmpresaIdLogada(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            // Produto/Conta ainda apontam para esta classe (FK em produção)
            return ResponseEntity.badRequest().body(Map.of("erro",
                    "Esta classe está vinculada a produtos ou contas e não pode ser excluída. Inative-a."));
        }
    }
}
