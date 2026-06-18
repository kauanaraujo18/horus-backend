package com.horus.projeto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContaPagarParcelaDTO {
    private Integer numeroParcela;
    private BigDecimal valorParcela;
    private LocalDate dataVencimento;
    /** Se null, herda o flag 'paga' da conta pai */
    private Boolean paga;
}
