package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "conta_pagar_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "contaPagar")
public class ContaPagarItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_item")
    private Long codItem;

    @ManyToOne
    @JoinColumn(name = "cod_conta_pagar", nullable = false)
    @JsonIgnore
    private ContaPagarEntity contaPagar;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cod_produto", nullable = false)
    private ProdutoEntity produto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "valor_unitario", nullable = false)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total_item", nullable = false)
    private BigDecimal valorTotalItem;
}
