package com.horus.projeto.repositories;

import com.horus.projeto.entities.CustoVendaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CustoVendaRepository extends JpaRepository<CustoVendaEntity, Long> {

    /** Guarda de idempotência: esta venda já teve o CMV apurado? */
    boolean existsByCodVendaAndEstornadoFalse(Long codVenda);

    /** Linhas ativas de uma venda — base do estorno em cascata. */
    List<CustoVendaEntity> findByCodVendaAndEstornadoFalse(Long codVenda);

    /** CMV total do período (regime de competência) — linha da DRE. */
    @Query("""
           SELECT COALESCE(SUM(c.custoTotal), 0)
           FROM CustoVendaEntity c
           WHERE c.empresa.id = :empresaId
             AND c.estornado = false
             AND c.dataMovimento BETWEEN :inicio AND :fim
           """)
    BigDecimal somarNoPeriodo(@Param("empresaId") Long empresaId,
                              @Param("inicio") LocalDate inicio,
                              @Param("fim") LocalDate fim);

    /** CMV agrupado por produto — base da margem por produto. [codProduto, custoTotal, quantidade] */
    @Query("""
           SELECT c.codProduto, COALESCE(SUM(c.custoTotal), 0), COALESCE(SUM(c.quantidade), 0)
           FROM CustoVendaEntity c
           WHERE c.empresa.id = :empresaId
             AND c.estornado = false
             AND c.dataMovimento BETWEEN :inicio AND :fim
           GROUP BY c.codProduto
           """)
    List<Object[]> somarPorProdutoNoPeriodo(@Param("empresaId") Long empresaId,
                                            @Param("inicio") LocalDate inicio,
                                            @Param("fim") LocalDate fim);

    /**
     * Diagnóstico: produtos que foram vendidos sem nenhuma base de custo.
     * [codProduto, nomeProduto, quantidadeVendida, receitaPerdida(sempre 0 aqui)]
     */
    @Query("""
           SELECT c.codProduto, MAX(c.nomeProduto), COALESCE(SUM(c.quantidade), 0), COUNT(c)
           FROM CustoVendaEntity c
           WHERE c.empresa.id = :empresaId
             AND c.estornado = false
             AND c.origemCusto = com.horus.projeto.enums.OrigemCusto.SEM_CUSTO
           GROUP BY c.codProduto
           ORDER BY COALESCE(SUM(c.quantidade), 0) DESC
           """)
    List<Object[]> produtosVendidosSemCusto(@Param("empresaId") Long empresaId);

    /** Quantas linhas de CMV ativas a empresa possui (usado no diagnóstico). */
    long countByEmpresaIdAndEstornadoFalse(Long empresaId);
}
