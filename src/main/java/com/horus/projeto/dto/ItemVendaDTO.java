package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data // Mantido o Lombok
@NoArgsConstructor
@AllArgsConstructor
public class ItemVendaDTO {
    
    private Long codProduto;
    private Integer quantidade;
    private BigDecimal valorUnitario;

    // --- SALVA-VIDAS: Getters Manuais ---
    // Adicionamos isso para o erro "undefined" sumir,
    // caso o Lombok falhe no seu IDE.

    public Long getCodProduto() {
        return codProduto;
    }

    public void setCodProduto(Long codProduto) {
        this.codProduto = codProduto;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public void setValorUnitario(BigDecimal valorUnitario) {
        this.valorUnitario = valorUnitario;
    }
}