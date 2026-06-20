package com.horus.projeto.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Demonstrativo de Fluxo de Caixa pronto para o frontend (3 seções + totalizadores). */
@Data
public class DfcResponseDTO {
    private LocalDate dataInicio;
    private LocalDate dataFim;

    private List<DfcMesDTO> meses = new ArrayList<>();      // colunas dinâmicas
    private DfcVariacaoDTO variacao = new DfcVariacaoDTO(); // rodapé de fechamento

    private List<DfcNodoDTO> receitas = new ArrayList<>();
    private List<DfcNodoDTO> custos   = new ArrayList<>();
    private List<DfcNodoDTO> despesas = new ArrayList<>();

    private BigDecimal totalReceitas = BigDecimal.ZERO;
    private BigDecimal totalCustos   = BigDecimal.ZERO;
    private BigDecimal totalDespesas = BigDecimal.ZERO;
    private BigDecimal resultadoCaixa = BigDecimal.ZERO; // receitas - custos - despesas
}
