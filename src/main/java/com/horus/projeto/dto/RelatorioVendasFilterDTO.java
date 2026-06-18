package com.horus.projeto.dto;

import lombok.Data;
import java.util.List;

@Data
public class RelatorioVendasFilterDTO {
    private String dataInicio;      // "yyyy-MM-dd"
    private String dataFim;         // "yyyy-MM-dd"
    private String formaPagamento;  // TODAS | DINHEIRO | PIX | CREDITO | DEBITO
    private List<Long> produtoIds;
}
