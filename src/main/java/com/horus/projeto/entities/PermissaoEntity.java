package com.horus.projeto.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "permissao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PermissaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // O nome será a chave que o FrontEnd vai ler (Ex: "PRODUTO_SALVAR", "RELATORIO_VER")
    @Column(unique = true, nullable = false, length = 50)
    private String nome; 

    // Uma breve explicação do que a permissão faz
    @Column(length = 150)
    private String descricao;
}