package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Item consumido em uma ordem de produção.
 * Guarda o snapshot do insumo e da quantidade usada,
 * permitindo estorno fiel mesmo que a composição mude depois.
 */
@Entity
@Table(name = "producao_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "producao")
public class ProducaoItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_item")
    private Long codItem;

    @ManyToOne
    @JoinColumn(name = "cod_producao", nullable = false)
    @JsonIgnore
    private ProducaoEntity producao;

    /** Insumo (MP) que foi consumido. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cod_insumo", nullable = false)
    private ProdutoEntity insumo;

    /** Quantidade do insumo efetivamente consumida. */
    @Column(name = "quantidade_consumida", nullable = false)
    private BigDecimal quantidadeConsumida;
}
