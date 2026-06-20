package com.horus.projeto.dto;

import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.enums.TipoClasse;
import lombok.Data;

/** Payload de criação/edição de uma classe do Plano de Contas. */
@Data
public class ClasseFinanceiraRequestDTO {
    private String nome;
    private TipoClasse tipo;       // RECEITA | CUSTO | DESPESA
    private NivelClasse nivel;     // SINTETICA | ANALITICA
    private Long codClassePai;     // null = raiz; senão deve ser uma sintética do mesmo tipo
    private String codigo;         // opcional; se vazio, é gerado automaticamente
}
