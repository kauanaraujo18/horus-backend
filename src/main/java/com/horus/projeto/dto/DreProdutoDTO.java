package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Linha da margem por produto dentro da DRE Gerencial. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DreProdutoDTO {
    private Long codProduto;
    private String nome;
    private BigDecimal quantidade;
    private BigDecimal receita;
    private BigDecimal cmv;
    private BigDecimal lucroBruto;
    private BigDecimal margem;
    /** true quando o CMV deste produto está zerado por falta de base de custo. */
    private boolean semBaseCusto;
}
