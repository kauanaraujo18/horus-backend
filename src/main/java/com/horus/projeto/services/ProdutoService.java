package com.horus.projeto.services;

import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.repositories.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor // Injeção de dependência automática do Repository via construtor
public class ProdutoService {

    private final ProdutoRepository repository;

    // Métodos de Leitura (não necessitam de transação de escrita)
    public List<ProdutoEntity> listarTodos() {
        return repository.findAll();
    }

    public ProdutoEntity buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrada com o ID: " + id));
    }

    // Métodos de Escrita (Transactional garante rollback em caso de erro)
    @Transactional
    public ProdutoEntity salvar(ProdutoEntity Produto) {
        // Regra de Negócio: Não permitir Codigo duplicado
        if (repository.existsByCodigo(Produto.getCodigo())) {
            throw new IllegalArgumentException("Erro: Já existe uma Produto cadastrada com este Codigo.");
        }
        return repository.save(Produto);
    }

    @Transactional
    public ProdutoEntity atualizar(Long id, ProdutoEntity ProdutoAtualizada) {
        ProdutoEntity ProdutoExistente = buscarPorId(id); // Reutiliza o método que já valida se existe

        // Atualização dos dados
        ProdutoExistente.setCodigo(ProdutoAtualizada.getCodigo());
        ProdutoExistente.setNome(ProdutoAtualizada.getNome());
        ProdutoExistente.setValor(ProdutoAtualizada.getValor());

        return repository.save(ProdutoExistente);
    }

    @Transactional
    public void deletar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Erro: Produto não encontrada para exclusão.");
        }
        repository.deleteById(id);
    }
}