package com.horus.projeto.controllers;

import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.repositories.ProdutoRepository;
import com.horus.projeto.services.ProdutoService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    // Agora injetamos o SERVICE, não o REPOSITORY
    private final ProdutoService service;

    @Autowired
    private ProdutoRepository repository;

    // @GetMapping
    // public ResponseEntity<List<ProdutoEntity>> listarTodos() {
    //     return ResponseEntity.ok(service.listarTodos());
    // }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoEntity> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ProdutoEntity>> listarProdutos(@RequestParam(value = "nome", required = false) String nome) {
        
        // Debug para garantir
        System.out.println("Endpoint único chamado. Filtro: " + nome);

        List<ProdutoEntity> lista;

        if (nome != null && !nome.isEmpty()) {
            // Se tem nome, filtra
            lista = repository.findByNomeContainingIgnoreCase(nome);
        } else {
            // Se não tem nome, traz tudo (substitui o antigo listarTodos)
            lista = repository.findAll();
        }

        return ResponseEntity.ok(lista);
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody ProdutoEntity Produto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.salvar(Produto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody ProdutoEntity Produto) {
        try {
            return ResponseEntity.ok(service.atualizar(id, Produto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/pesquisar")
    public ResponseEntity<List<ProdutoEntity>> pesquisarProdutos(@RequestParam String termo) {
        List<ProdutoEntity> produtos = repository.findByNomeContainingIgnoreCase(termo);
        return ResponseEntity.ok(produtos);
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<?> buscarPorCodigo(@PathVariable String codigo) {
        // Tenta achar o produto
        Optional<ProdutoEntity> produto = repository.findByCodigo(codigo);
        
        if (produto.isPresent()) {
            return ResponseEntity.ok(produto.get());
        } else {
            return ResponseEntity.notFound().build(); // Retorna Erro 404 se não achar
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        try {
            service.deletar(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}