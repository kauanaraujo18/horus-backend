package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "produto_venda")
@Data // Gera Getters, Setters, toString, equals e hashcode
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoVendaEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_item_venda")
    private Long codItemVenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cod_venda", nullable = false)
    @JsonIgnore // Evita loop infinito no JSON
    @ToString.Exclude // Evita loop infinito no toString do @Data
    private VendaEntity venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cod_produto", nullable = false)
    private ProdutoEntity produto;

    @Column(name = "quantidade", nullable = false)
    private Integer quantidade;

    @Column(name = "valor_unitario", nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total_item", nullable = false)
    private BigDecimal valorTotalItem;
}