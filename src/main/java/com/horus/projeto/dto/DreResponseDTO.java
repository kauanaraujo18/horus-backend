package com.horus.projeto.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DRE Gerencial (regime de COMPETÊNCIA na operação de venda).
 *
 * Estrutura:
 *   Receita Bruta de Vendas
 *   (−) Descontos concedidos
 *   (+) Acréscimos
 *   (=) Receita Líquida
 *   (−) CMV
 *   (=) Lucro Bruto            -> margemBruta %
 *   (−) Despesas Operacionais
 *   (=) Resultado Operacional
 *
 * As Despesas vêm do razão de CAIXA (lancamento_financeiro, tipo DESPESA). As compras
 * de mercadoria (tipo CUSTO) NÃO entram: elas são estoque e viram resultado só quando
 * o item é vendido, na linha do CMV. É essa troca que separa esta DRE do DFC.
 */
@Data
public class DreResponseDTO {

    private LocalDate dataInicio;
    private LocalDate dataFim;

    private long quantidadeVendas;

    private BigDecimal receitaBruta = BigDecimal.ZERO;
    private BigDecimal descontos = BigDecimal.ZERO;
    private BigDecimal acrescimos = BigDecimal.ZERO;
    private BigDecimal receitaLiquida = BigDecimal.ZERO;

    private BigDecimal cmv = BigDecimal.ZERO;
    private BigDecimal lucroBruto = BigDecimal.ZERO;
    /** Lucro Bruto ÷ Receita Líquida × 100. */
    private BigDecimal margemBruta = BigDecimal.ZERO;

    private BigDecimal despesasOperacionais = BigDecimal.ZERO;
    private BigDecimal resultadoOperacional = BigDecimal.ZERO;
    /** Resultado Operacional ÷ Receita Líquida × 100. */
    private BigDecimal margemOperacional = BigDecimal.ZERO;

    /** Ticket médio do período (Receita Líquida ÷ quantidade de vendas). */
    private BigDecimal ticketMedio = BigDecimal.ZERO;

    private List<DreProdutoDTO> produtos = new ArrayList<>();

    // ── Sinalização de confiabilidade do número ─────────────────────────────
    /** Vendas do período que ainda não tiveram o CMV apurado (precisam de reprocessamento). */
    private long vendasSemCmv;
    /** Produtos vendidos no período cuja base de custo está ausente (CMV subestimado). */
    private int produtosSemCusto;
    /** Parte da receita do período que veio de produtos sem base de custo. */
    private BigDecimal receitaSemCusto = BigDecimal.ZERO;
}
