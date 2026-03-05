package com.horus.projeto.controllers;

import com.horus.projeto.services.EtiquetaService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
    public ResponseEntity<byte[]> gerarEtiqueta(
            @PathVariable Long codProduto,
            @RequestParam(defaultValue = "16") int qtd) {

        try {
            byte[] pdfBytes = etiquetaService.gerarEtiquetasPorId(codProduto, qtd);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=etiquetas_produto_" + codProduto + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

}