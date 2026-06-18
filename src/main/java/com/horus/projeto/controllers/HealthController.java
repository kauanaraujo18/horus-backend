package com.horus.projeto.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<String> ping() {
        // Responde imediatamente com HTTP 200 OK sem acessar o banco de dados
        return ResponseEntity.ok("Horus Backend is awake!");
    }
}