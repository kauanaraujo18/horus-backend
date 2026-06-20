package com.horus.projeto.controllers;

import com.horus.projeto.dto.LancamentoManualDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.LancamentoFinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/financeiro/lancamentos")
@RequiredArgsConstructor
public class LancamentoFinanceiroController {

    private final LancamentoFinanceiroService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    @PostMapping
    public ResponseEntity<?> lancarManual(@RequestBody LancamentoManualDTO dto) {
        try {
            return ResponseEntity.ok(service.postarManual(getEmpresaIdLogada(), dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/{id}/estorno")
    public ResponseEntity<?> estornar(@PathVariable Long id) {
        try {
            service.estornar(getEmpresaIdLogada(), id);
            return ResponseEntity.ok(Map.of("mensagem", "Lançamento estornado."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
