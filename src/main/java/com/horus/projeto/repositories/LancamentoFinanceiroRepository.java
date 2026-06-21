package com.horus.projeto.repositories;

import com.horus.projeto.entities.LancamentoFinanceiroEntity;
import com.horus.projeto.enums.OrigemLancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiroEntity, Long> {

    /**
     * Núcleo do DFC: soma (magnitude) por classe analítica no período, ignorando estornados.
     * Cada classe analítica tem movimentos de uma única direção (RECEITA→ENTRADA, CUSTO/DESPESA→SAIDA),
     * então SUM(valor) já é o total correto da conta. A árvore (sintéticas) é somada em memória.
     * Retorna linhas [codClasse (Long), total (BigDecimal)].
     */
    @Query("""
           SELECT l.codClasse, SUM(l.valor)
           FROM LancamentoFinanceiroEntity l
           WHERE l.empresa.id = :empresaId
             AND l.estornado = false
             AND l.dataMovimento BETWEEN :inicio AND :fim
           GROUP BY l.codClasse
           """)
    List<Object[]> somarPorClasseNoPeriodo(@Param("empresaId") Long empresaId,
                                           @Param("inicio") LocalDate inicio,
                                           @Param("fim") LocalDate fim);

    /**
     * Saldo (assinado) acumulado de todos os lançamentos antes de uma data — base do
     * "Saldo Inicial das Contas" no rodapé do DFC. ENTRADA soma, SAÍDA subtrai.
     */
    @Query("""
           SELECT COALESCE(SUM(CASE WHEN l.tipoMovimento = com.horus.projeto.enums.TipoMovimento.ENTRADA
                                    THEN l.valor ELSE -l.valor END), 0)
           FROM LancamentoFinanceiroEntity l
           WHERE l.empresa.id = :empresaId AND l.estornado = false AND l.dataMovimento < :data
           """)
    java.math.BigDecimal saldoAssinadoAntesDe(@Param("empresaId") Long empresaId,
                                              @Param("data") LocalDate data);

    /** Variação assinada por dia no período — base do gráfico de evolução de saldo. */
    @Query("""
           SELECT l.dataMovimento,
                  SUM(CASE WHEN l.tipoMovimento = com.horus.projeto.enums.TipoMovimento.ENTRADA
                           THEN l.valor ELSE -l.valor END)
           FROM LancamentoFinanceiroEntity l
           WHERE l.empresa.id = :empresaId AND l.estornado = false
             AND l.dataMovimento BETWEEN :inicio AND :fim
           GROUP BY l.dataMovimento
           ORDER BY l.dataMovimento
           """)
    List<Object[]> somarAssinadoPorDia(@Param("empresaId") Long empresaId,
                                       @Param("inicio") LocalDate inicio,
                                       @Param("fim") LocalDate fim);

    /** Saldo assinado do razão agrupado por conta financeira (base do saldo atual por conta). */
    @Query("""
           SELECT l.codContaFinanceira,
                  SUM(CASE WHEN l.tipoMovimento = com.horus.projeto.enums.TipoMovimento.ENTRADA
                           THEN l.valor ELSE -l.valor END)
           FROM LancamentoFinanceiroEntity l
           WHERE l.empresa.id = :empresaId AND l.estornado = false AND l.codContaFinanceira IS NOT NULL
           GROUP BY l.codContaFinanceira
           """)
    List<Object[]> somarAssinadoPorConta(@Param("empresaId") Long empresaId);

    /** Guarda de exclusão da classe: existe lançamento histórico nela? */
    boolean existsByCodClasse(Long codClasse);

    /** Guarda de exclusão da conta: existe movimentação nela? */
    boolean existsByCodContaFinanceira(Long codContaFinanceira);

    /** Lançamentos ativos gerados por uma origem específica (para estorno em cascata). */
    List<LancamentoFinanceiroEntity> findByOrigemIdAndEstornadoFalse(Long origemId);

    /** Versão escopada pelo tipo de origem — evita colisão de id entre VENDA e CONTA_PAGAR. */
    List<LancamentoFinanceiroEntity> findByOrigemAndOrigemIdAndEstornadoFalse(OrigemLancamento origem, Long origemId);

    /** Existe lançamento ativo para esta origem? (usado no reprocessamento de vendas antigas) */
    boolean existsByOrigemAndOrigemIdAndEstornadoFalse(OrigemLancamento origem, Long origemId);
}
