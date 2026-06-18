package com.horus.projeto.controllers;

import com.horus.projeto.dto.RelatorioContasPagarFilterDTO;
import com.horus.projeto.dto.RelatorioVendasFilterDTO;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.services.RelatorioPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioPdfService pdfService;

    private Long getEmpresaId() {
        var usuario = (UsuarioEntity) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return usuario.getEmpresa().getId();
    }

    @PostMapping("/vendas")
    public ResponseEntity<?> relatorioVendas(@RequestBody RelatorioVendasFilterDTO filtro) {
        try {
            byte[] pdf = pdfService.gerarPdfVendas(filtro, getEmpresaId());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"relatorio-vendas.pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/contas-pagar")
    public ResponseEntity<?> relatorioContasPagar(@RequestBody RelatorioContasPagarFilterDTO filtro) {
        try {
            byte[] pdf = pdfService.gerarPdfContasPagar(filtro, getEmpresaId());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"relatorio-contas-pagar.pdf\"")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("erro", e.getMessage()));
        }
    }
}
