package com.horus.projeto.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Conciliação de uma conta: movimentos + saldos comparativos. */
@Data
public class ConciliacaoResponseDTO {
    private Long codConta;
    private String nomeConta;
    private BigDecimal saldoInicial   = BigDecimal.ZERO;
    private BigDecimal saldoSistema    = BigDecimal.ZERO; // inicial + todos os movimentos
    private BigDecimal saldoConciliado = BigDecimal.ZERO; // inicial + apenas os conciliados
    private BigDecimal diferenca       = BigDecimal.ZERO; // sistema − conciliado
    private List<MovimentoConciliacaoDTO> movimentos = new ArrayList<>();
}
