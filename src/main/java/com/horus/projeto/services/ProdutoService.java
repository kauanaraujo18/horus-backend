package com.horus.projeto.services;

import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.ProdutoRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repository;
    private final EmpresaRepository empresaRepository; 

    public List<ProdutoEntity> listarPorEmpresa(Long empresaId) {
        return repository.findByEmpresaId(empresaId);
    }    

    public ProdutoEntity buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));
    }

    @Transactional
    public ProdutoEntity salvar(ProdutoEntity produto, Long empresaId) {
        if (produto.getCodProduto() != null) {
            return atualizar(produto.getCodProduto(), produto);
        }

        if (repository.existsByCodigo(produto.getCodigo())) {
            throw new IllegalArgumentException("Erro: Já existe um Produto cadastrado com este Codigo.");
        }

        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);
        produto.setEmpresa(empresa);
        
        // A anotação @PrePersist na Entity cuidará de definir null como 0, 
        // mas se o usuário enviar a quantidade (ex: 10), ela será respeitada e salva.

        return repository.save(produto);
    }

    @Transactional
    public ProdutoEntity atualizar(Long id, ProdutoEntity produtoAtualizada) {
        ProdutoEntity produtoExistente = buscarPorId(id); 

        if (!produtoExistente.getCodigo().equals(produtoAtualizada.getCodigo())) {
            if (repository.existsByCodigo(produtoAtualizada.getCodigo())) {
                throw new IllegalArgumentException("Erro: Já existe um Produto cadastrado com este Codigo.");
            }
        }

        produtoExistente.setCodigo(produtoAtualizada.getCodigo());
        produtoExistente.setNome(produtoAtualizada.getNome());
        produtoExistente.setValor(produtoAtualizada.getValor());
        
        // Atualiza a quantidade manualmente se o usuário alterar na tela de produtos
        if(produtoAtualizada.getQuantidadeEstoque() != null) {
             produtoExistente.setQuantidadeEstoque(produtoAtualizada.getQuantidadeEstoque());
        }

        return repository.save(produtoExistente);
    }

    @Transactional
    public void deletar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Erro: Produto não encontrado para exclusão.");
        }
        repository.deleteById(id);
    }
}