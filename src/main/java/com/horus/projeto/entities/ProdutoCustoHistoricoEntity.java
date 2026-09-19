package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.horus.projeto.enums.OrigemEntradaEstoque;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Trilha de auditoria do custo médio ponderado móvel.
 *
 * Uma linha por ENTRADA de estoque, guardando os dois lados da conta
 * (o que havia + o que entrou = o novo custo). É o que permite responder
 * "por que o custo deste produto é R$ 6,37?" meses depois — sem isso o
 * custo médio seria um número mágico que ninguém consegue auditar.
 *
 * Append-only: nunca é editada nem apagada.
 */
@Entity
@Table(name = "produto_custo_historico")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoCustoHistoricoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_historico")
    private Long codHistorico;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    @Column(name = "cod_produto", nullable = false)
    private Long codProduto;

    // ── Situação ANTES da entrada ────────────────────────────────────────
    @Column(name = "quantidade_anterior", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidadeAnterior;

    @Column(name = "custo_anterior", nullable = false, precision = 15, scale = 4)
    private BigDecimal custoAnterior;

    // ── O que entrou ─────────────────────────────────────────────────────
    @Column(name = "quantidade_entrada", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidadeEntrada;

    @Column(name = "custo_entrada", nullable = false, precision = 15, scale = 4)
    private BigDecimal custoEntrada;

    // ── Resultado da ponderação ──────────────────────────────────────────
    @Column(name = "quantidade_nova", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantidadeNova;

    @Column(name = "custo_novo", nullable = false, precision = 15, scale = 4)
    private BigDecimal custoNovo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemEntradaEstoque origem;

    /** Id do documento que originou a entrada (cod_conta_pagar, cod_producao, cod_venda...). */
    @Column(name = "origem_id")
    private Long origemId;

    @Column(length = 200)
    private String descricao;

    @Column(name = "data_movimento", nullable = false)
    private LocalDate dataMovimento;

    @Column(name = "data_registro", updatable = false)
    private LocalDateTime dataRegistro;

    @PrePersist
    public void prePersist() {
        this.dataRegistro = LocalDateTime.now();
        if (this.dataMovimento == null) this.dataMovimento = LocalDate.now();
    }
}
