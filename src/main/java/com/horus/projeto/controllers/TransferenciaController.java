package com.horus.projeto.controllers;

import com.horus.projeto.dto.TransferenciaRequestDTO;
import com.horus.projeto.entities.TransferenciaEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.TransferenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/financeiro/transferencias")
@RequiredArgsConstructor
public class TransferenciaController {

    private final TransferenciaService service;

    private Long getEmpresaIdLogada() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    @GetMapping
    public ResponseEntity<List<TransferenciaEntity>> listar() {
        return ResponseEntity.ok(service.listar(getEmpresaIdLogada()));
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody TransferenciaRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.criar(getEmpresaIdLogada(), dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/estornar")
    public ResponseEntity<?> estornar(@PathVariable Long id) {
        try {
            service.estornar(getEmpresaIdLogada(), id);
            return ResponseEntity.ok(Map.of("mensagem", "Transferência estornada."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
