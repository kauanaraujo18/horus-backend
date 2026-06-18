package com.horus.projeto.dto;

import lombok.Data;
import java.util.List;

@Data
public class RelatorioContasPagarFilterDTO {
    private String vencInicio;  // "yyyy-MM-dd"
    private String vencFim;     // "yyyy-MM-dd"
    private String status;      // TODOS | PAGA | VENCIDA | EM_ABERTO
    private List<Long> produtoIds;
}
