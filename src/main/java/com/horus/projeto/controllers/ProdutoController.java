package com.horus.projeto.controllers;

import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.repositories.ProdutoRepository;
import com.horus.projeto.services.ProdutoService;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProdutoController {
    
    private final ProdutoService service;

    @Autowired
    private ProdutoRepository repository;

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoEntity> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========================================================================
    // ENDPOINT ATUALIZADO: Agora suporta a busca em tempo real do Spotlight
    // ========================================================================
    @GetMapping
    public ResponseEntity<List<ProdutoEntity>> listarTodos(@RequestParam(required = false) String nome) {
        // 1. Pescamos o usuário logado da memória do Spring Security
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        // 2. Extraímos o ID da empresa dele
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
        
        // 3. Se houver um termo de pesquisa, usa a query nova (Tempo Real)
        if (nome != null && !nome.trim().isEmpty()) {
            List<ProdutoEntity> resultados = repository.buscarPorNomeOuCodigoEEmpresa(nome, idEmpresaLogada);
            return ResponseEntity.ok(resultados);
        }
        
        // 4. Se não houver termo (Grid Inicial), lista todos os produtos da empresa
        List<ProdutoEntity> produtos = service.listarPorEmpresa(idEmpresaLogada);
        return ResponseEntity.ok(produtos);
    }

    @PostMapping
    public ResponseEntity<ProdutoEntity> salvar(@RequestBody ProdutoEntity produto) {
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
        ProdutoEntity produtoSalvo = service.salvar(produto, idEmpresaLogada);
        return ResponseEntity.ok(produtoSalvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoEntity> atualizar(@PathVariable Long id, @RequestBody ProdutoEntity produto) {
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
        produto.setCodProduto(id); 
        ProdutoEntity produtoAtualizado = service.salvar(produto, idEmpresaLogada);
        return ResponseEntity.ok(produtoAtualizado);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProdutoEntity> atualizarEstoqueParcial(@PathVariable Long id, @RequestBody Map<String, Integer> payload) {
        try {
            var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
            ProdutoEntity produtoExistente = service.buscarPorId(id);

            if (!produtoExistente.getEmpresa().getId().equals(idEmpresaLogada)) {
                return ResponseEntity.status(403).build(); 
            }

            if (payload.containsKey("quantidadeEstoque")) {
                produtoExistente.setQuantidadeEstoque(payload.get("quantidadeEstoque"));
                ProdutoEntity produtoAtualizado = repository.save(produtoExistente);
                return ResponseEntity.ok(produtoAtualizado);
            }
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Nota: O endpoint antigo "/pesquisar" foi mantido mas não é mais necessário para o fluxo principal.
    @GetMapping("/pesquisar")
    public ResponseEntity<List<ProdutoEntity>> pesquisarProdutos(@RequestParam String termo) {
        List<ProdutoEntity> produtos = repository.findByNomeContainingIgnoreCase(termo);
        return ResponseEntity.ok(produtos);
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<?> buscarPorCodigo(@PathVariable String codigo) {
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();
        Optional<ProdutoEntity> produto = repository.findByCodigoAndEmpresaId(codigo, idEmpresaLogada);
        
        if (produto.isPresent()) {
            return ResponseEntity.ok(produto.get());
        } else {
            return ResponseEntity.notFound().build(); 
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