package com.horus.projeto.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta do endpoint de pré-visualização de produção.
 * Permite ao frontend mostrar quais insumos serão consumidos,
 * a disponibilidade de estoque e se a produção pode ser realizada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProducaoCalculoDTO {

    private Long codProduto;
    private String nomeProduto;
    private String tipoProduto;
    private Integer quantidadeSolicitada;
    /** true somente se TODOS os insumos têm estoque suficiente. */
    private boolean podeRealizar;
    private List<InsumoNecessarioDTO> insumos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsumoNecessarioDTO {
        private Long codInsumo;
        private String nomeInsumo;
        private BigDecimal quantidadeNecessaria;
        private Integer quantidadeDisponivel;
        /** true se quantidadeDisponivel >= quantidadeNecessaria */
        private boolean suficiente;
    }
}
