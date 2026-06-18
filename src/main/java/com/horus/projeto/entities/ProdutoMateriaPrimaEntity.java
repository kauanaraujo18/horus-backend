package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "materia_prima")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoMateriaPrimaEntity {

    @EmbeddedId
    private MateriaPrimaId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("codProdutoFinal")
    @JoinColumn(name = "cod_produto_final")
    @JsonIgnore
    private ProdutoEntity produtoFinal;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("codProdutoMateriaPrima")
    @JoinColumn(name = "cod_produto_materia_prima")
    private ProdutoEntity materiaPrima;

    @Column(name = "quantidade", nullable = false)
    private BigDecimal quantidade;
}
