package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.horus.projeto.enums.OrigemCusto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CMV — Custo da Mercadoria Vendida (razão de COMPETÊNCIA).
 *
 * Uma linha por item vendido, gravada no momento da venda com o custo unitário
 * VIGENTE naquele instante (snapshot). O snapshot é o ponto central: quando o
 * custo do produto mudar amanhã, o resultado do mês passado continua correto.
 *
 * ATENÇÃO — por que isto NÃO é um LancamentoFinanceiroEntity:
 * o razão financeiro é regime de CAIXA e governa o saldo das contas. O dinheiro
 * da mercadoria já saiu quando a parcela do Contas a Pagar foi baixada
 * ("Compras de Mercadorias"). Lançar o CMV lá contaria a mesma saída duas vezes
 * e quebraria o fechamento do DFC. Por isso o CMV vive em um razão próprio,
 * classificado pelo mesmo Plano de Contas, e alimenta a DRE — nunca o DFC.
 *
 * Append-only, no mesmo padrão do razão: nunca é editado, apenas estornado.
 */
@Entity
@Table(name = "custo_venda")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustoVendaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_custo_venda")
    private Long codCustoVenda;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    /** Venda que originou o custo (rastreabilidade e estorno em cascata). */
    @Column(name = "cod_venda", nullable = false)
    private Long codVenda;

    /** Item da venda (pode ser nulo em vendas antigas reprocessadas). */
    @Column(name = "cod_item_venda")
    private Long codItemVenda;

    @Column(name = "cod_produto", nullable = false)
    private Long codProduto;

    /** Nome do produto no momento da venda — sobrevive a renomeação/exclusão. */
    @Column(name = "nome_produto", length = 255)
    private String nomeProduto;

    /** Classe analítica de CUSTO (CMV) usada na classificação. Pode ser nula. */
    @Column(name = "cod_classe")
    private Long codClasse;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;

    /** Custo unitário congelado no momento da venda. */
    @Column(name = "custo_unitario", nullable = false, precision = 15, scale = 4)
    private BigDecimal custoUnitario;

    /** quantidade × custoUnitario, arredondado a 2 casas. */
    @Column(name = "custo_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal custoTotal;

    /** Data da COMPETÊNCIA = data da venda (eixo temporal da DRE). */
    @Column(name = "data_movimento", nullable = false)
    private LocalDate dataMovimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_custo", nullable = false, length = 12)
    private OrigemCusto origemCusto;

    @Column(nullable = false)
    private Boolean estornado = false;

    @Column(name = "data_registro", updatable = false)
    private LocalDateTime dataRegistro;

    @PrePersist
    public void prePersist() {
        this.dataRegistro = LocalDateTime.now();
        if (this.estornado == null) this.estornado = false;
        if (this.origemCusto == null) this.origemCusto = OrigemCusto.SEM_CUSTO;
    }
}
