package com.horus.projeto.enums;

/**
 * De onde veio a base de custo usada no CMV de um item vendido.
 * Fica gravado no snapshot para auditoria — permite saber, meses depois,
 * se aquele custo era confiável ou se o produto estava sem custo cadastrado.
 */
public enum OrigemCusto {
    /** Custo médio ponderado móvel, acumulado pelas entradas reais de estoque. */
    CUSTO_MEDIO,
    /** Custo veio do campo valor_custo do próprio produto (sem composição). */
    CADASTRO,
    /** Custo apurado pela explosão recursiva da composição (BOM). */
    COMPOSICAO,
    /** Custo apurado = 0: não há valor de custo nem composição com custo. */
    SEM_CUSTO
}
