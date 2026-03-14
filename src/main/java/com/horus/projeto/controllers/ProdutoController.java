package com.horus.projeto.controllers;

import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.repositories.ProdutoRepository;
import com.horus.projeto.services.ProdutoService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public ResponseEntity<List<ProdutoEntity>> listarTodos() {
        // 1. Pescamos o usuário logado da memória do Spring Security
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        // 2. Extraímos o ID da empresa dele! Adeus simulação!
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
        
        // Agora chamamos o Service passando o ID da empresa
        List<ProdutoEntity> produtos = service.listarPorEmpresa(idEmpresaLogada);
        
        return ResponseEntity.ok(produtos);
    }

    @PostMapping
    public ResponseEntity<ProdutoEntity> salvar(@RequestBody ProdutoEntity produto) {
        // 1. Pescamos o usuário logado
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        // 2. Extraímos a empresa
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
        
        // Manda o produto e a empresa para o Service
        ProdutoEntity produtoSalvo = service.salvar(produto, idEmpresaLogada);
        
        return ResponseEntity.ok(produtoSalvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoEntity> atualizar(@PathVariable Long id, @RequestBody ProdutoEntity produto) {
        // 1. Pescamos o usuário logado
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        // 2. Extraímos a empresa
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
        
        // Garante que o produto vai ser atualizado no ID correto
        produto.setCodProduto(id); 
        
        // Chama o Service passando a empresa
        ProdutoEntity produtoAtualizado = service.salvar(produto, idEmpresaLogada);
        
        return ResponseEntity.ok(produtoAtualizado);
    }

    @GetMapping("/pesquisar")
    public ResponseEntity<List<ProdutoEntity>> pesquisarProdutos(@RequestParam String termo) {
        List<ProdutoEntity> produtos = repository.findByNomeContainingIgnoreCase(termo);
        return ResponseEntity.ok(produtos);
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<?> buscarPorCodigo(@PathVariable String codigo) {
        
        // 1. Captura o usuário logado e a empresa dele
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();

        // 2. Tenta achar o produto pelo código E garantindo que é da empresa logada
        Optional<ProdutoEntity> produto = repository.findByCodigoAndEmpresaId(codigo, idEmpresaLogada);
        
        if (produto.isPresent()) {
            return ResponseEntity.ok(produto.get());
        } else {
            return ResponseEntity.notFound().build(); // Retorna Erro 404 se não achar ou se for de outra empresa
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