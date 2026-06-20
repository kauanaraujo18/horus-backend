package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.horus.projeto.enums.TipoConta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Conta Financeira — local onde o dinheiro da empresa fica (Caixa físico ou Banco). */
@Entity
@Table(name = "conta_financeira")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContaFinanceiraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_conta")
    private Long codConta;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_conta", nullable = false, length = 10)
    private TipoConta tipoConta;

    @Column(name = "saldo_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean ativo = true;

    @PrePersist
    public void prePersist() {
        if (this.ativo == null) this.ativo = true;
        if (this.saldoInicial == null) this.saldoInicial = BigDecimal.ZERO;
    }
}
