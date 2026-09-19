package com.horus.projeto.controllers;

import com.horus.projeto.config.SchemaCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final SchemaCheck schemaCheck;

    @GetMapping
    public ResponseEntity<String> ping() {
        // Responde imediatamente com HTTP 200 OK sem acessar o banco de dados
        return ResponseEntity.ok("Horus Backend is awake!");
    }

    /**
     * Diz quais migrações .sql ainda não foram aplicadas NESTE banco.
     * Serve para conferir um ambiente remoto (Render/Supabase) sem acesso ao servidor:
     * basta abrir o endpoint autenticado e ler a lista.
     */
    @GetMapping("/schema")
    public ResponseEntity<Map<String, Object>> schema() {
        return ResponseEntity.ok(schemaCheck.diagnostico());
    }
}
