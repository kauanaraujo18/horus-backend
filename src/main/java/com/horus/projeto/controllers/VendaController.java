package com.horus.projeto.controllers;

import com.horus.projeto.dto.VendaRequestDTO;
import com.horus.projeto.entities.VendaEntity;
import com.horus.projeto.services.VendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.horus.projeto.dto.VendaResponseDTO;
import java.util.List;

@RestController
@RequestMapping("/api/vendas")
@CrossOrigin(origins = "*") // Libera acesso para seu HTML local
public class VendaController {

    @Autowired
    private VendaService vendaService;

    @PostMapping
    public ResponseEntity<?> registrarVenda(@RequestBody VendaRequestDTO vendaDTO) {
        try {
            VendaEntity novaVenda = vendaService.registrarVenda(vendaDTO);
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
        List<VendaResponseDTO> lista = vendaService.listarTodasVendas();
        return ResponseEntity.ok(lista);
    }
}