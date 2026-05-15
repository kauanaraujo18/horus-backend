package com.horus.projeto.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "valor", nullable = false)
    private BigDecimal valor;

    // NOVO CAMPO: Mapeamento do Estoque
    @Column(name = "quantidade_estoque", nullable = false)
    private Integer quantidadeEstoque;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;
    
    @PrePersist
    public void prePersist() {
        // Garantia arquitetural: Todo produto nasce com estoque preenchido, mesmo que não enviado
        if (this.quantidadeEstoque == null) {
            this.quantidadeEstoque = 0;
        }
    }
}