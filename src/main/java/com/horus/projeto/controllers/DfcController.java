package com.horus.projeto.controllers;

import com.horus.projeto.dto.DfcResponseDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.DfcService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/financeiro/dfc")
@RequiredArgsConstructor
public class DfcController {

    private final DfcService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    /** Ex.: GET /api/financeiro/dfc?inicio=2026-06-01&fim=2026-06-30 */
    @GetMapping
    public ResponseEntity<?> gerar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        try {
            DfcResponseDTO dfc = service.gerar(getEmpresaIdLogada(), inicio, fim);
            return ResponseEntity.ok(dfc);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /** Série de evolução do saldo da empresa para o gráfico de linhas do dashboard. */
    @GetMapping("/saldo-evolucao")
    public ResponseEntity<?> evolucaoSaldo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        try {
            return ResponseEntity.ok(service.evolucaoSaldo(getEmpresaIdLogada(), inicio, fim));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
