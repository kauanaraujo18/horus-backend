package com.horus.projeto.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MateriaPrimaId implements Serializable {

    @Column(name = "cod_produto_final")
    private Long codProdutoFinal;

    @Column(name = "cod_produto_materia_prima")
    private Long codProdutoMateriaPrima;
}
