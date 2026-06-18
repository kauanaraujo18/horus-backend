package com.horus.projeto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProducaoRequestDTO {
    /** ID do produto a ser produzido (deve ser PF ou MPPF). */
    private Long codProduto;
    /** Quantidade de unidades a produzir. */
    private Integer quantidade;
}
