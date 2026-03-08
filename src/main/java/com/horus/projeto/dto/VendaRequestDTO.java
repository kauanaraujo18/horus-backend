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
    
    // O empresaId foi removido. O Controller é quem vai injetar isso de forma segura!
}