package com.horus.projeto.controllers;

import com.horus.projeto.dto.MateriaPrimaItemDTO;
import com.horus.projeto.dto.ProdutoEsquemaNodeDTO;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.ProdutoMateriaPrimaEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.enums.TipoProduto;
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
public class ProdutoController {
    
    private final ProdutoService service;

    @Autowired
    private ProdutoRepository repository;

    private Long getEmpresaIdLogada() {
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return usuarioLogado.getEmpresa().getId();
    }

    @GetMapping("/esquema")
    public ResponseEntity<Map<String, Object>> esquema() {
        List<ProdutoEsquemaNodeDTO> raizes = service.buildEsquema(getEmpresaIdLogada());
        return ResponseEntity.ok(Map.of(
                "nome", "Todos os Produtos",
                "tipo", "ROOT",
                "children", raizes
        ));
    }

    @GetMapping("/analise-lucro")
    public ResponseEntity<List<Map<String, Object>>> analiseLucro() {
        return ResponseEntity.ok(service.analiseLucro(getEmpresaIdLogada()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoEntity> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id, getEmpresaIdLogada()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ProdutoEntity>> listarTodos(@RequestParam(required = false) String nome) {
        Long idEmpresaLogada = getEmpresaIdLogada();
        
        if (nome != null && !nome.trim().isEmpty()) {
            List<ProdutoEntity> resultados = repository.buscarPorNomeOuCodigoEEmpresa(nome, idEmpresaLogada);
            return ResponseEntity.ok(resultados);
        }
        
        List<ProdutoEntity> produtos = service.listarPorEmpresa(idEmpresaLogada);
        return ResponseEntity.ok(produtos);
    }

    @PostMapping
    public ResponseEntity<?> salvar(@RequestBody ProdutoEntity produto) {
        try {
            return ResponseEntity.ok(service.salvar(produto, getEmpresaIdLogada()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable Long id, @RequestBody ProdutoEntity produto) {
        try {
            produto.setCodProduto(id);
            return ResponseEntity.ok(service.salvar(produto, getEmpresaIdLogada()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProdutoEntity> atualizarEstoqueParcial(@PathVariable Long id, @RequestBody Map<String, Integer> payload) {
        try {
            // Usa o método blindado do service que já garante a posse da empresa
            ProdutoEntity produtoExistente = service.buscarPorId(id, getEmpresaIdLogada());

            if (payload.containsKey("quantidadeEstoque")) {
                produtoExistente.setQuantidadeEstoque(payload.get("quantidadeEstoque"));
                ProdutoEntity produtoAtualizado = repository.save(produtoExistente);
                return ResponseEntity.ok(produtoAtualizado);
            }
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build(); // Retorna 403 para não revelar se o ID existe ou não
        }
    }

    @GetMapping("/pesquisar")
    public ResponseEntity<List<ProdutoEntity>> pesquisarProdutos(@RequestParam String termo) {
        List<ProdutoEntity> produtos = repository.findByNomeContainingIgnoreCaseAndEmpresaId(termo, getEmpresaIdLogada());
        return ResponseEntity.ok(produtos);
    }

    @GetMapping("/codigo/{codigo}")
    public ResponseEntity<?> buscarPorCodigo(@PathVariable String codigo) {
        Optional<ProdutoEntity> produto = repository.findByCodigoAndEmpresaId(codigo, getEmpresaIdLogada());
        
        if (produto.isPresent()) {
            return ResponseEntity.ok(produto.get());
        } else {
            return ResponseEntity.notFound().build(); 
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        try {
            service.deletar(id, getEmpresaIdLogada());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // =========================================================
    // MÓDULO DE PRODUÇÃO - Matérias-Primas
    // =========================================================

    @GetMapping("/{id}/materias-primas")
    public ResponseEntity<List<ProdutoMateriaPrimaEntity>> listarMateriasPrimas(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.listarMateriasPrimas(id, getEmpresaIdLogada()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // Substitui toda a composição do produto (body: [{id, quantidade}, ...])
    @PutMapping("/{id}/materias-primas")
    public ResponseEntity<?> vincularMateriasPrimas(@PathVariable Long id,
                                                     @RequestBody List<MateriaPrimaItemDTO> itens) {
        try {
            return ResponseEntity.ok(service.vincularMateriasPrimas(id, itens, getEmpresaIdLogada()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Acesso Negado")) {
                return ResponseEntity.status(403).body(Map.of("erro", e.getMessage()));
            }
            return ResponseEntity.status(500).body(Map.of("erro", "Erro interno ao vincular matérias-primas: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/materias-primas/{mpId}")
    public ResponseEntity<ProdutoEntity> desvincularMateriaPrima(@PathVariable Long id,
                                                                  @PathVariable Long mpId) {
        try {
            return ResponseEntity.ok(service.desvincularMateriaPrima(id, mpId, getEmpresaIdLogada()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // Retorna apenas produtos que podem ser usados como matéria-prima (MP ou MPPF)
    @GetMapping("/materias-primas-disponiveis")
    public ResponseEntity<List<ProdutoEntity>> listarMateriaisPrimasDisponiveis() {
        return ResponseEntity.ok(service.listarMateriaisPrimasDisponiveis(getEmpresaIdLogada()));
    }

    @GetMapping("/tipo/{tipo}")
    public ResponseEntity<List<ProdutoEntity>> listarPorTipo(@PathVariable String tipo) {
        try {
            TipoProduto tipoProduto = TipoProduto.valueOf(tipo.toUpperCase());
            return ResponseEntity.ok(service.listarPorTipo(tipoProduto, getEmpresaIdLogada()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}