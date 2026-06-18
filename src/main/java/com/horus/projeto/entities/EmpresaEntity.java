package com.horus.projeto.entities; 

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.LocalDate; // Importação necessária para a data de nascimento

@Entity
@Table(name = "empresa")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class EmpresaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razao_social", nullable = false, length = 150)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 150)
    private String nomeFantasia;

    @Column(unique = true, length = 18)
    private String cnpj;

    @Column(name = "nome_proprietario", length = 150)
    private String nomeProprietario;

    @Column(name = "telefone_proprietario", length = 20)
    private String telefoneProprietario;

    @Column(name = "email_proprietario", length = 150)
    private String emailProprietario;

    @Column(name = "cpf_proprietario", length = 14)
    private String cpfProprietario;

    @Column(name = "data_nascimento_proprietario")
    private LocalDate dataNascimentoProprietario;

    private Boolean ativo = true;

    // Logomarca exclusiva da empresa (usada nas etiquetas). Nunca serializada no JSON padrão.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "logo")
    private byte[] logo;

    @Column(name = "logo_content_type", length = 60)
    private String logoContentType;

    @com.fasterxml.jackson.annotation.JsonProperty("possuiLogo")
    public boolean isPossuiLogo() {
        return logo != null && logo.length > 0;
    }

    public String getNome() {
        // Implementação ajustada para não quebrar a aplicação caso esse método seja chamado.
        // Ele tenta retornar o Nome Fantasia; se estiver nulo, devolve a Razão Social.
        return (this.nomeFantasia != null && !this.nomeFantasia.trim().isEmpty()) 
                ? this.nomeFantasia 
                : this.razaoSocial;
    } 
}