package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.horus.projeto.enums.OrigemLancamento;
import com.horus.projeto.enums.TipoMovimento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lançamento Financeiro (Razão / livro-caixa).
 * Append-only: nunca é editado/deletado — correções entram como ESTORNO.
 * É a única fonte de verdade do DFC.
 */
@Entity
@Table(name = "lancamento_financeiro")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LancamentoFinanceiroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_lancamento")
    private Long codLancamento;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    /** id da classe ANALÍTICA classificada (trava garantida no service). */
    @Column(name = "cod_classe", nullable = false)
    private Long codClasse;

    /** Conta financeira onde o dinheiro entrou/saiu (dá os saldos por conta). */
    @Column(name = "cod_conta_financeira")
    private Long codContaFinanceira;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimento", nullable = false, length = 8)
    private TipoMovimento tipoMovimento;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    /** Data do caixa (recebimento/pagamento) — eixo temporal do DFC. */
    @Column(name = "data_movimento", nullable = false)
    private LocalDate dataMovimento;

    @Column(length = 200)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrigemLancamento origem;

    /** Rastreabilidade para a origem (cod_venda, cod_parcela, etc.). */
    @Column(name = "origem_id")
    private Long origemId;

    @Column(nullable = false)
    private Boolean estornado = false;

    /** Conciliação bancária (marcação manual). */
    @Column(nullable = false)
    private Boolean conciliado = false;

    @Column(name = "data_conciliacao")
    private java.time.LocalDate dataConciliacao;

    /** Quando este lançamento É um estorno, aponta para o lançamento revertido. */
    @Column(name = "cod_estorno_de")
    private Long codEstornoDe;

    @Column(name = "data_registro", updatable = false)
    private LocalDateTime dataRegistro;

    @PrePersist
    public void prePersist() {
        this.dataRegistro = LocalDateTime.now();
        if (this.estornado == null) this.estornado = false;
    }
}
