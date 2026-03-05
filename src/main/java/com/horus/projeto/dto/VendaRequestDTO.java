package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendaRequestDTO {

    private Integer qtdParcelas;
    private BigDecimal desconto;
    private BigDecimal acrescimo;
    private List<ItemVendaDTO> itens;
    private BigDecimal valorPago;
    private BigDecimal valorDinheiro;
    private BigDecimal valorPix;
    private BigDecimal valorCredito;
    private BigDecimal valorDebito;

    public Integer getQtdParcelas() {
        return qtdParcelas;
    }

    public void setQtdParcelas(Integer qtdParcelas) {
        this.qtdParcelas = qtdParcelas;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public void setDesconto(BigDecimal desconto) {
        this.desconto = desconto;
    }

    public BigDecimal getAcrescimo() {
        return acrescimo;
    }

    public void setAcrescimo(BigDecimal acrescimo) {
        this.acrescimo = acrescimo;
    }

    public List<ItemVendaDTO> getItens() {
        return itens;
    }

    public void setItens(List<ItemVendaDTO> itens) {
        this.itens = itens;
    }

    public BigDecimal getValorPago() { 
        return valorPago; 
    }

    public void setValorPago(BigDecimal valorPago) { 
        this.valorPago = valorPago; 
    }

}