package com.horus.projeto.controllers;

import com.horus.projeto.dto.DreResponseDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.CusteioService;
import com.horus.projeto.services.DreService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * DRE Gerencial + operações de custeio (CMV).
 * Todo endpoint resolve a empresa pelo JWT — nunca por parâmetro do cliente.
 */
@RestController
@RequestMapping("/api/financeiro/dre")
@RequiredArgsConstructor
public class DreController {

    private final DreService dreService;
    private final CusteioService custeioService;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    /** Ex.: GET /api/financeiro/dre?inicio=2026-09-01&fim=2026-09-30 */
    @GetMapping
    public ResponseEntity<?> gerar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        try {
            DreResponseDTO dre = dreService.gerar(getEmpresaIdLogada(), inicio, fim);
            return ResponseEntity.ok(dre);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Backfill do CMV das vendas anteriores ao módulo de custeio. Idempotente.
     * Usa o custo ATUAL dos produtos — vendas novas congelam o custo no ato.
     */
    @PostMapping("/reprocessar-cmv")
    public ResponseEntity<?> reprocessarCmv() {
        try {
            int n = custeioService.reprocessar(getEmpresaIdLogada());
            return ResponseEntity.ok(Map.of("reprocessadas", n));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /** Aponta o que impede a margem de ser confiável (produtos sem base de custo). */
    @GetMapping("/diagnostico-custeio")
    public ResponseEntity<?> diagnostico() {
        return ResponseEntity.ok(custeioService.diagnostico(getEmpresaIdLogada()));
    }
}
