package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "conta_pagar_parcela")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "contaPagar")
public class ContaPagarParcelaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_parcela")
    private Long codParcela;

    @ManyToOne
    @JoinColumn(name = "cod_conta_pagar", nullable = false)
    @JsonIgnore
    private ContaPagarEntity contaPagar;

    @Column(name = "numero_parcela", nullable = false)
    private Integer numeroParcela;

    @Column(name = "valor_parcela", nullable = false)
    private BigDecimal valorParcela;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(nullable = false)
    private Boolean paga = false;

    /** Data real do pagamento — regime de caixa data a saída pelo dia em que pagou. */
    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;
}
