package com.horus.projeto.repositories;

import com.horus.projeto.entities.ProdutoVendaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProdutoVendaRepository extends JpaRepository<ProdutoVendaEntity, Long> {

    /**
     * Receita bruta por produto no período (vendas não estornadas).
     * Não inclui desconto/acréscimo, que são do cabeçalho da venda e não do item.
     * Retorna [codProduto (Long), nome (String), quantidade (Long), receita (BigDecimal)].
     */
    @Query("""
           SELECT i.produto.codProduto, i.produto.nome,
                  COALESCE(SUM(i.quantidade), 0), COALESCE(SUM(i.valorTotalItem), 0)
           FROM ProdutoVendaEntity i
           WHERE i.venda.empresa.id = :empresaId
             AND i.venda.estornada = false
             AND i.venda.dataVenda BETWEEN :inicio AND :fim
           GROUP BY i.produto.codProduto, i.produto.nome
           """)
    List<Object[]> somarPorProdutoNoPeriodo(@Param("empresaId") Long empresaId,
                                            @Param("inicio") LocalDateTime inicio,
                                            @Param("fim") LocalDateTime fim);
}
