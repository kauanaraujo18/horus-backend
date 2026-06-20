package com.horus.projeto.enums;

/** Origem que gerou o lançamento financeiro (rastreabilidade). */
public enum OrigemLancamento {
    VENDA,         // entrada automática gerada pelo PDV
    CONTA_PAGAR,   // saída automática gerada ao pagar uma parcela
    MANUAL,        // lançamento avulso feito pelo usuário
    ESTORNO        // linha de reversão de um lançamento anterior
}
