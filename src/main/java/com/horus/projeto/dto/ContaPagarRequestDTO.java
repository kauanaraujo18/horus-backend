package com.horus.projeto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContaPagarRequestDTO {
    private String descricao;
    private String fornecedor;
    private String numeroNotaFiscal;
    /** true = conta já paga no ato do cadastro */
    private Boolean paga;
    /** Classe financeira ANALÍTICA (CUSTO/DESPESA) do título — classifica a saída no DFC. */
    private Long codClasse;
    /** Conta financeira de onde o dinheiro sai (obrigatória quando há parcela paga). */
    private Long codContaFinanceira;
    private List<ContaPagarItemDTO> itens;
    private List<ContaPagarParcelaDTO> parcelas;
}
