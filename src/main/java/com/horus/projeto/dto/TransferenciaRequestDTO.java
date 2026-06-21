package com.horus.projeto.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Payload de uma transferência de saldo entre contas. */
@Data
public class TransferenciaRequestDTO {
    private Long codContaOrigem;
    private Long codContaDestino;
    private BigDecimal valor;
    private LocalDate data;
    private String descricao;
}
