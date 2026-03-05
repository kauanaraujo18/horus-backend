package com.horus.projeto.dto;

import java.math.BigDecimal;

public class ItemVendaResponseDTO {
    private String nome; // Nome do Produto
    private Long codProduto;
    private Integer quantidade;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotalItem;

    // Getters e Setters
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public Long getCodProduto() { return codProduto; }
    public void setCodProduto(Long codProduto) { this.codProduto = codProduto; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public BigDecimal getValorUnitario() { return valorUnitario; }
    public void setValorUnitario(BigDecimal valorUnitario) { this.valorUnitario = valorUnitario; }

    public BigDecimal getValorTotalItem() { return valorTotalItem; }
    public void setValorTotalItem(BigDecimal valorTotalItem) { this.valorTotalItem = valorTotalItem; }
}