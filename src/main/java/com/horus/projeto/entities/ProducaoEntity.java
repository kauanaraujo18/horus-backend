package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de uma ordem de produção.
 * Quando o usuário produz X unidades de um PF ou MPPF,
 * o sistema cria este registro, debita os insumos (MPs) e
 * adiciona a quantidade ao estoque do produto final.
 */
@Entity
@Table(name = "producao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"itensConsumidos", "empresa"})
public class ProducaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_producao")
    private Long codProducao;

    /** Produto que foi produzido (PF ou MPPF). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cod_produto", nullable = false)
    private ProdutoEntity produto;

    @Column(name = "quantidade_produzida", nullable = false)
    private Integer quantidadeProduzida;

    @Column(name = "data_producao", nullable = false, updatable = false)
    private LocalDateTime dataProducao;

    /**
     * Estornada indica que a produção foi revertida:
     * os insumos foram devolvidos e o estoque do produto decrementado.
     */
    @Column(nullable = false)
    private Boolean estornada = false;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    /** Snapshot dos insumos consumidos — essencial para viabilizar estorno. */
    @OneToMany(mappedBy = "producao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ProducaoItemEntity> itensConsumidos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.dataProducao = LocalDateTime.now();
        if (this.estornada == null) this.estornada = false;
        if (this.itensConsumidos == null) this.itensConsumidos = new ArrayList<>();
    }
}
