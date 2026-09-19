package com.horus.projeto.enums;

/**
 * Evento que fez estoque ENTRAR e, por consequência, recalculou o custo médio.
 * Saídas (venda, consumo em produção) não aparecem aqui: saída não altera o
 * custo médio, apenas reduz a quantidade.
 */
public enum OrigemEntradaEstoque {
    /** Compra lançada no Contas a Pagar — custo = valor unitário da nota. */
    COMPRA,
    /** Produção concluída — custo = insumos realmente consumidos ÷ quantidade produzida. */
    PRODUCAO,
    /** Estorno de venda — o item volta pelo custo com que saiu (snapshot do CMV). */
    ESTORNO_VENDA,
    /** Estorno de produção — o insumo volta pelo custo com que foi consumido. */
    ESTORNO_PRODUCAO,
    /** Custo informado manualmente pelo usuário no cadastro do produto. */
    AJUSTE_MANUAL,
    /** Semente inicial na migração (custo cadastrado virou custo médio). */
    SALDO_INICIAL
}
