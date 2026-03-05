package com.horus.projeto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class VendaResponseDTO {
    private Long codVenda;
    private LocalDateTime dataVenda;
    private BigDecimal valorTotal;
    private BigDecimal desconto;
    private BigDecimal acrescimo;
    
    // Formas de Pagamento Discriminadas
    private BigDecimal valorDinheiro;
    private BigDecimal valorPix;
    private BigDecimal valorCredito;
    private BigDecimal valorDebito;
    private BigDecimal valorPago;
    private BigDecimal troco;

    // Lista de Itens
    private List<ItemVendaResponseDTO> itens;

    // Getters e Setters
    public Long getCodVenda() { return codVenda; }
    public void setCodVenda(Long codVenda) { this.codVenda = codVenda; }

    public LocalDateTime getDataVenda() { return dataVenda; }
    public void setDataVenda(LocalDateTime dataVenda) { this.dataVenda = dataVenda; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getDesconto() { return desconto; }
    public void setDesconto(BigDecimal desconto) { this.desconto = desconto; }

    public BigDecimal getAcrescimo() { return acrescimo; }
    public void setAcrescimo(BigDecimal acrescimo) { this.acrescimo = acrescimo; }

    public BigDecimal getValorDinheiro() { return valorDinheiro; }
    public void setValorDinheiro(BigDecimal valorDinheiro) { this.valorDinheiro = valorDinheiro; }

    public BigDecimal getValorPix() { return valorPix; }
    public void setValorPix(BigDecimal valorPix) { this.valorPix = valorPix; }

    public BigDecimal getValorCredito() { return valorCredito; }
    public void setValorCredito(BigDecimal valorCredito) { this.valorCredito = valorCredito; }

    public BigDecimal getValorDebito() { return valorDebito; }
    public void setValorDebito(BigDecimal valorDebito) { this.valorDebito = valorDebito; }
    
    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public BigDecimal getTroco() { return troco; }
    public void setTroco(BigDecimal troco) { this.troco = troco; }

    public List<ItemVendaResponseDTO> getItens() { return itens; }
    public void setItens(List<ItemVendaResponseDTO> itens) { this.itens = itens; }
}