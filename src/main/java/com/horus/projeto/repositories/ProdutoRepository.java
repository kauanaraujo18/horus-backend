package com.horus.projeto.repositories;

import com.horus.projeto.entities.ProdutoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    
    Optional<ProdutoEntity> findByCodigoAndEmpresaId(String codigo, Long empresaId);

    List<ProdutoEntity> findByEmpresaId(Long empresaId);

    // ========================================================================
    // NOVA QUERY: Busca em tempo real por nome ou código, protegida por Empresa
    // ========================================================================
    @Query("SELECT p FROM ProdutoEntity p WHERE p.empresa.id = :empresaId AND (LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) OR p.codigo LIKE CONCAT('%', :termo, '%'))")
    List<ProdutoEntity> buscarPorNomeOuCodigoEEmpresa(@Param("termo") String termo, @Param("empresaId") Long empresaId);
}