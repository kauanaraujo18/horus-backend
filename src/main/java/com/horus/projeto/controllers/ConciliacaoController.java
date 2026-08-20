package com.horus.projeto.controllers;

import com.horus.projeto.dto.ConciliacaoResponseDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.ConciliacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/financeiro/conciliacao")
@RequiredArgsConstructor
public class ConciliacaoController {

    private final ConciliacaoService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    /** Movimentos + saldos comparativos de uma conta. */
    @GetMapping("/{codConta}")
    public ResponseEntity<?> conciliacao(@PathVariable Long codConta) {
        try {
            ConciliacaoResponseDTO dto = service.conciliacaoDaConta(getEmpresaIdLogada(), codConta);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/lancamento/{id}")
    public ResponseEntity<?> conciliarLancamento(@PathVariable Long id, @RequestParam boolean conciliado) {
        try {
            service.conciliarLancamento(getEmpresaIdLogada(), id, conciliado);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/transferencia/{id}")
    public ResponseEntity<?> conciliarTransferencia(@PathVariable Long id, @RequestParam boolean conciliado) {
        try {
            service.conciliarTransferencia(getEmpresaIdLogada(), id, conciliado);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
