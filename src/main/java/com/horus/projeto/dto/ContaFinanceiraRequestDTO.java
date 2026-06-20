package com.horus.projeto.dto;

import com.horus.projeto.enums.TipoConta;
import lombok.Data;

import java.math.BigDecimal;

/** Payload de criação/edição de uma Conta Financeira. */
@Data
public class ContaFinanceiraRequestDTO {
    private String nome;
    private TipoConta tipoConta;     // CAIXA | BANCO
    private BigDecimal saldoInicial; // saldo de abertura (opcional, default 0)
}
