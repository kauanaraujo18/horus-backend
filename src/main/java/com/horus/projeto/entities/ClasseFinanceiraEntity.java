package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.enums.TipoClasse;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Classe Financeira (Plano de Contas).
 *
 * Auto-relacionamento via {@code codClassePai} (mapeado como id puro, não como
 * @ManyToOne, para a árvore do DFC ser montada em memória sem recursão de JSON
 * nem joins eager — mesmo padrão do esquema de produções).
 */
@Entity
@Table(name = "classe_financeira")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClasseFinanceiraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_classe")
    private Long codClasse;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoClasse tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NivelClasse nivel;

    /** id da classe sintética pai (null = raiz). */
    @Column(name = "cod_classe_pai")
    private Long codClassePai;

    @Column(length = 20)
    private String codigo;

    @Column(nullable = false)
    private Boolean ativo = true;

    @PrePersist
    public void prePersist() {
        if (this.ativo == null) this.ativo = true;
    }
}
