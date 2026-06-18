package com.horus.projeto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContaPagarItemDTO {
    private Long codProduto;
    private Integer quantidade;
    private BigDecimal valorUnitario;
}
