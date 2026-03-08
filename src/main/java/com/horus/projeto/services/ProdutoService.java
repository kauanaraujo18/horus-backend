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
    private final EmpresaRepository empresaRepository; // O Spring injeta automaticamente pelo RequiredArgsConstructor

    public List<ProdutoEntity> listarPorEmpresa(Long empresaId) {
        return repository.findByEmpresaId(empresaId);
    }    

    public ProdutoEntity buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));
    }

    // --- MÉTODOS DE ESCRITA ---

    @Transactional
    public ProdutoEntity salvar(ProdutoEntity produto, Long empresaId) {
        // INTELIGÊNCIA APLICADA: Se o produto veio com ID preenchido, significa que o 
        // Controller mandou uma EDIÇÃO para cá. Redirecionamos para o método correto!
        if (produto.getCodProduto() != null) {
            return atualizar(produto.getCodProduto(), produto);
        }

        // Regra de Negócio: Não permitir Codigo duplicado para produtos NOVOS
        if (repository.existsByCodigo(produto.getCodigo())) {
            throw new IllegalArgumentException("Erro: Já existe um Produto cadastrado com este Codigo.");
        }

        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);
        produto.setEmpresa(empresa);

        return repository.save(produto);
    }

    @Transactional
    public ProdutoEntity atualizar(Long id, ProdutoEntity produtoAtualizada) {
        ProdutoEntity produtoExistente = buscarPorId(id); 

        // Regra de Negócio: Só checamos se o código existe se o usuário TROCOU o código na tela
        if (!produtoExistente.getCodigo().equals(produtoAtualizada.getCodigo())) {
            if (repository.existsByCodigo(produtoAtualizada.getCodigo())) {
                throw new IllegalArgumentException("Erro: Já existe um Produto cadastrado com este Codigo.");
            }
        }

        // Atualização dos dados (Não precisamos setar a empresa de novo, pois já está no banco)
        produtoExistente.setCodigo(produtoAtualizada.getCodigo());
        produtoExistente.setNome(produtoAtualizada.getNome());
        produtoExistente.setValor(produtoAtualizada.getValor());

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