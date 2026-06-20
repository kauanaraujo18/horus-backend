package com.horus.projeto.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lançamento financeiro avulso (entrada/saída manual no razão). */
@Data
public class LancamentoManualDTO {
    private Long codClasse;          // deve ser uma classe ANALÍTICA
    private BigDecimal valor;        // sempre positivo; a direção vem do tipo da classe
    private LocalDate dataMovimento; // data do caixa
    private String descricao;
}
