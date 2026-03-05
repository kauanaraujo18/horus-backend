package com.horus.projeto.controllers;

import com.horus.projeto.barcode.GeradorCodigoBarras;
import com.horus.projeto.barcode.RelatorioRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/relatorios")
@CrossOrigin(origins = "*") 
public class BarcodeController {

    // Injeção dos serviços de geração de PDF
    @Autowired
    private GeradorCodigoBarras geradorBarcodeService;



    // --- ENDPOINT EXISTENTE: CÓDIGO DE BARRAS ---
    @PostMapping("/codigo-barras")
    public ResponseEntity<byte[]> gerarRelatorioBarcode(@RequestBody RelatorioRequest request) {
        try {
            byte[] pdfBytes = geradorBarcodeService.gerarPdfStream(request.getQuantidade(), request.getPosicao());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "barcodes.pdf");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

}