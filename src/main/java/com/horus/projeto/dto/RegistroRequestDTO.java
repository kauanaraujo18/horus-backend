package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRequestDTO {
    private String nomeProprietario;
    private String emailProprietario;
    private String telefoneProprietario;
    private String dataNascimentoProprietario;
    private String cpfProprietario;
    private String cnpj;
    private String razaoSocial;
    private String nomeFantasia;
}