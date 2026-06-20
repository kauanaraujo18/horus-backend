package com.horus.projeto.controllers;

import com.horus.projeto.entities.ParametrosFinanceirosEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.ParametrosFinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
}
