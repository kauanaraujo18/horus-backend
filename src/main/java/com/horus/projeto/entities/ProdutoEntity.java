package com.horus.projeto.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.horus.projeto.enums.ReferenciaMedida;
import com.horus.projeto.enums.TipoProduto;
import com.horus.projeto.enums.UnidadeMedida;

import java.io.Serializable;

@Entity
@Table(name = "produto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_produto")
    private Long codProduto;

    @Column(name = "codigo", nullable = true)
    private String codigo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "valor", nullable = false)
    private BigDecimal valor;

    @Column(name = "valor_custo")
    private BigDecimal valorCusto;

    @Column(name = "quantidade_estoque", nullable = false)
    private Integer quantidadeEstoque;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 4)
    private TipoProduto tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidade_medida", length = 20)
    private UnidadeMedida unidadeMedida;

    @Enumerated(EnumType.STRING)
    @Column(name = "referencia", length = 30)
    private ReferenciaMedida referencia;

    /** Classe financeira ANALÍTICA (RECEITA) padrão, usada para classificar a venda no PDV. */
    @Column(name = "cod_classe_padrao")
    private Long codClassePadrao;

    @OneToMany(mappedBy = "produtoFinal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ProdutoMateriaPrimaEntity> composicao = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.quantidadeEstoque == null) {
            this.quantidadeEstoque = 0;
        }
        if (this.tipo == null) {
            this.tipo = TipoProduto.R;
        }
        if (this.composicao == null) {
            this.composicao = new ArrayList<>();
        }
    }
}
