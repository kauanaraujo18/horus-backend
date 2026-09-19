package com.horus.projeto.controllers;

import com.horus.projeto.entities.ParametrosFinanceirosEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.ParametrosFinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/financeiro/parametros")
@RequiredArgsConstructor
public class ParametrosFinanceiroController {

    private final ParametrosFinanceiroService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    /** Retorna as contas padrão (caixa/banco) da empresa — usado pelo frontend para marcar os padrões. */
    @GetMapping
    public ResponseEntity<ParametrosFinanceirosEntity> obter() {
        return ResponseEntity.ok(service.obter(getEmpresaIdLogada()));
    }

    /**
     * Define a classe analítica de CUSTO que receberá o CMV.
     * Body: { "codClasse": 12 } — enviar null limpa e volta para a resolução por convenção.
     */
    @PutMapping("/classe-cmv")
    public ResponseEntity<?> definirClasseCmv(@RequestBody Map<String, Long> body) {
        try {
            service.definirClasseCmv(getEmpresaIdLogada(), body.get("codClasse"));
            return ResponseEntity.ok(Map.of("mensagem", "Classe de CMV atualizada."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
