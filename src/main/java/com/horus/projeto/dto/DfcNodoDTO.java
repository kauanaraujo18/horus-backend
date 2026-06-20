package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Nó da árvore do DFC. Analítica = valor próprio; Sintética = soma dos filhos. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DfcNodoDTO {
    private Long codClasse;
    private String codigo;
    private String nome;
    private String tipo;   // RECEITA | CUSTO | DESPESA
    private String nivel;  // SINTETICA | ANALITICA
    private BigDecimal valor;                          // consolidado (Σ meses)
    private List<BigDecimal> valoresPorMes = new ArrayList<>(); // alinhado a DfcResponseDTO.meses
    private List<DfcNodoDTO> filhos = new ArrayList<>();
}
