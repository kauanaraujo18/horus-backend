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
    private List<ContaPagarItemDTO> itens;
    private List<ContaPagarParcelaDTO> parcelas;
}
