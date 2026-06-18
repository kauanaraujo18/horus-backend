package com.horus.projeto.controllers;

import com.horus.projeto.dto.ProducaoCalculoDTO;
import com.horus.projeto.dto.ProducaoRequestDTO;
import com.horus.projeto.entities.ProducaoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.ProducaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/producao")
@RequiredArgsConstructor
public class ProducaoController {

    private final ProducaoService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    /** Lista o histórico de produções da empresa. */
    @GetMapping
    public ResponseEntity<List<ProducaoEntity>> listar() {
        return ResponseEntity.ok(service.listarPorEmpresa(getEmpresaIdLogada()));
    }

    /**
     * Pré-visualiza os insumos necessários para uma produção.
     * Não altera nenhum estoque — use antes de confirmar.
     */
    @PostMapping("/calcular")
    public ResponseEntity<?> calcular(@RequestBody ProducaoRequestDTO dto) {
        try {
            ProducaoCalculoDTO resultado = service.calcular(
                    dto.getCodProduto(), dto.getQuantidade(), getEmpresaIdLogada());
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    /** Executa a produção: debita insumos e credita produto final. */
    @PostMapping
    public ResponseEntity<?> realizarProducao(@RequestBody ProducaoRequestDTO dto) {
        try {
            ProducaoEntity producao = service.realizarProducao(
                    dto.getCodProduto(), dto.getQuantidade(), getEmpresaIdLogada());
            return ResponseEntity.ok(producao);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    /** Estorna uma produção: devolve insumos e remove do estoque do PF/MPPF. */
    @PatchMapping("/{id}/estornar")
    public ResponseEntity<?> estornar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.estornar(id, getEmpresaIdLogada()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("erro", e.getMessage()));
        }
    }
}
