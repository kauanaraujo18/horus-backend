package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um ponto da série de evolução do saldo da empresa (para o gráfico de linhas). */
@Data
@AllArgsConstructor
public class SaldoPontoDTO {
    private LocalDate data;
    private BigDecimal saldo;
}
