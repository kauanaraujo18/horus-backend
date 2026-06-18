package com.horus.projeto.repositories;

import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.enums.TipoProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ProdutoRepository extends JpaRepository<ProdutoEntity, Long> {
    
    // Método personalizado antigo (mantido por retrocompatibilidade)
    Optional<ProdutoEntity> findByCodigo(String codigo);
    
    boolean existsByCodigo(String codigo);

    List<ProdutoEntity> findByNomeContainingIgnoreCase(String nome);
    
    Optional<ProdutoEntity> findByCodigoAndEmpresaId(String codigo, Long empresaId);

    List<ProdutoEntity> findByEmpresaId(Long empresaId);

    // ========================================================================
    // QUERIES BLINDADAS - MULTI-TENANT
    // ========================================================================
    @Query("SELECT p FROM ProdutoEntity p WHERE p.empresa.id = :empresaId AND (LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) OR p.codigo LIKE CONCAT('%', :termo, '%'))")
    List<ProdutoEntity> buscarPorNomeOuCodigoEEmpresa(@Param("termo") String termo, @Param("empresaId") Long empresaId);

    // CORREÇÃO DO ERRO: Ensinando o Spring a mapear 'id' para 'codProduto'
    @Query("SELECT p FROM ProdutoEntity p WHERE p.codProduto = :id AND p.empresa.id = :empresaId")
    Optional<ProdutoEntity> findByIdAndEmpresaId(@Param("id") Long id, @Param("empresaId") Long empresaId);
    
    boolean existsByCodigoAndEmpresaId(String codigo, Long empresaId);

    List<ProdutoEntity> findByNomeContainingIgnoreCaseAndEmpresaId(String nome, Long empresaId);

    List<ProdutoEntity> findByTipoAndEmpresaId(TipoProduto tipo, Long empresaId);

    @Query("SELECT p FROM ProdutoEntity p WHERE p.empresa.id = :empresaId AND p.tipo IN :tipos")
    List<ProdutoEntity> findByTiposAndEmpresaId(@Param("tipos") List<TipoProduto> tipos, @Param("empresaId") Long empresaId);
}