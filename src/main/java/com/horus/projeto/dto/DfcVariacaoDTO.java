package com.horus.projeto.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rodapé de variação do fluxo de caixa:
 * Saldo Inicial das Contas + Entradas − Custos − Despesas = Saldo Final.
 * Valores consolidados + listas por mês (alinhadas a DfcResponseDTO.meses; saldos encadeiam).
 */
@Data
public class DfcVariacaoDTO {
    private BigDecimal saldoInicial  = BigDecimal.ZERO;
    private BigDecimal totalEntradas = BigDecimal.ZERO;
    private BigDecimal totalCustos   = BigDecimal.ZERO;
    private BigDecimal totalDespesas = BigDecimal.ZERO;
    private BigDecimal saldoFinal    = BigDecimal.ZERO;

    private List<BigDecimal> saldoInicialMes = new ArrayList<>();
    private List<BigDecimal> entradasMes     = new ArrayList<>();
    private List<BigDecimal> custosMes       = new ArrayList<>();
    private List<BigDecimal> despesasMes     = new ArrayList<>();
    private List<BigDecimal> saldoFinalMes   = new ArrayList<>();
}
