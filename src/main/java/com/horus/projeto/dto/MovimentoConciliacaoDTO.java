package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma linha da conciliação (lançamento do razão ou transferência) vista da conta. */
@Data
@AllArgsConstructor
public class MovimentoConciliacaoDTO {
    private String tipo;          // LANCAMENTO | TRANSFERENCIA
    private Long id;              // codLancamento ou codTransferencia
    private LocalDate data;
    private String descricao;
    private BigDecimal valor;     // assinado p/ a conta (+ entrada / − saída)
    private Boolean conciliado;
}
