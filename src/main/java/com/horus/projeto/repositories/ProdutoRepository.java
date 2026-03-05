package com.horus.projeto.repositories;

import com.horus.projeto.entities.ProdutoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ProdutoRepository extends JpaRepository<ProdutoEntity, Long> {
    
    // Método personalizado para buscar por codigo, já que é um campo único
    Optional<ProdutoEntity> findBycodigo(String codigo);
    
    // Verifica se já existe o codigo (útil para validações antes de salvar)
    boolean existsByCodigo(String codigo);

    List<ProdutoEntity> findByNomeContainingIgnoreCase(String nome);
    
    Optional<ProdutoEntity> findByCodigo(String codigo);
}