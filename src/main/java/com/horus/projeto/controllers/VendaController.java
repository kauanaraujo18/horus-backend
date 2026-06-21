package com.horus.projeto.controllers;

import com.horus.projeto.dto.VendaRequestDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.entities.VendaEntity;
import com.horus.projeto.services.VendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.horus.projeto.dto.VendaResponseDTO;
import java.util.List;

@RestController
@RequestMapping("/api/vendas")
public class VendaController {

    @Autowired
    private VendaService vendaService;

    @PostMapping
    public ResponseEntity<?> registrarVenda(@RequestBody VendaRequestDTO vendaDTO) {
        try {
            // 1. Pescamos o usuário logado
            var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            
            // 2. Extraímos a empresa
            Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();

            // Passamos o DTO e a Empresa simulada para a regra de negócio
            VendaEntity novaVenda = vendaService.registrarVenda(vendaDTO, idEmpresaLogada);
            
            return ResponseEntity.ok("Venda registrada com sucesso! Código: " + novaVenda.getCodVenda());
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Erro ao registrar venda: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace(); // Mostra erro no console do servidor para debug
            return ResponseEntity.internalServerError().body("Erro interno no servidor.");
        }
    }

    @GetMapping
    public ResponseEntity<List<VendaResponseDTO>> listarVendas() {
        // 1. Pescamos o usuário logado
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. Extraímos a empresa
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();

        return ResponseEntity.ok(vendaService.listarPorEmpresa(idEmpresaLogada));
    }

    @PatchMapping("/{id}/estornar")
    public ResponseEntity<?> estornarVenda(@PathVariable Long id) {
        try {
            var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
            vendaService.estornarVenda(id, idEmpresaLogada);
            return ResponseEntity.ok(java.util.Map.of("mensagem", "Venda estornada com sucesso."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("erro", e.getMessage()));
        }
    }
}