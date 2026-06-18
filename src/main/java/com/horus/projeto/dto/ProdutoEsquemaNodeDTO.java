package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class ProdutoEsquemaNodeDTO {
    private Long codProduto;
    private String nome;
    private String tipo;
    private Integer estoque;
    private BigDecimal quantidade; // quantidade consumida na composição do pai (null para raízes)
    private List<ProdutoEsquemaNodeDTO> children;
}
