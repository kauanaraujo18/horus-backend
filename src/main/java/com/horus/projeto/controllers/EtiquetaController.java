package com.horus.projeto.controllers;

import com.horus.projeto.services.EtiquetaService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/etiquetas")
@CrossOrigin(origins = "*")
public class EtiquetaController {

    @Autowired
    private EtiquetaService etiquetaService;

    // Exemplo de chamada: /api/etiquetas/gerar/10?qtd=16
    @GetMapping("/gerar/{codProduto}")
    public ResponseEntity<?> gerarEtiqueta(
            @PathVariable Long codProduto,
            @RequestParam(defaultValue = "16") int qtd) {

        // 🛡️ TRAVA DE SEGURANÇA NA PORTA DA API
        if (qtd > 320) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("O limite máximo permitido é de 320 etiquetas por impressão.");
        }

        try {
            byte[] pdfBytes = etiquetaService.gerarEtiquetasPorId(codProduto, qtd);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=etiquetas_produto_" + codProduto + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            // Retorna o erro real para o painel do Frontend
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno ao gerar etiquetas: " + e.getMessage());
        }
    }
}