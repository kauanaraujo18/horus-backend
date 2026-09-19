package com.horus.projeto.services;

import com.horus.projeto.dto.DreProdutoDTO;
import com.horus.projeto.dto.DreResponseDTO;
import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.enums.TipoClasse;
import com.horus.projeto.repositories.ClasseFinanceiraRepository;
import com.horus.projeto.repositories.CustoVendaRepository;
import com.horus.projeto.repositories.LancamentoFinanceiroRepository;
import com.horus.projeto.repositories.ProdutoVendaRepository;
import com.horus.projeto.repositories.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Motor da DRE Gerencial — o resultado do negócio em regime de COMPETÊNCIA.
 *
 * Diferença essencial para o DFC:
 *   DFC  (DfcService)  = dinheiro que entrou e saiu no período. Compra de mercadoria
 *                        aparece no dia em que foi PAGA.
 *   DRE  (aqui)        = resultado do que foi VENDIDO no período. A mercadoria aparece
 *                        como CMV no dia em que foi VENDIDA, pelo custo congelado.
 *
 * Os dois não se anulam nem se contradizem: respondem perguntas diferentes
 * ("tenho caixa?" × "estou lucrando?") e por isso vivem em razões separados.
 */
@Service
@RequiredArgsConstructor
public class DreService {

    private final VendaRepository vendaRepository;
    private final ProdutoVendaRepository produtoVendaRepository;
    private final CustoVendaRepository custoVendaRepository;
    private final LancamentoFinanceiroRepository lancamentoRepository;
    private final ClasseFinanceiraRepository classeRepository;

    public DreResponseDTO gerar(Long empresaId, LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null)
            throw new IllegalArgumentException("Período (início e fim) é obrigatório.");
        if (inicio.isAfter(fim))
            throw new IllegalArgumentException("A data inicial não pode ser maior que a final.");

        LocalDateTime ini = inicio.atStartOfDay();
        LocalDateTime end = fim.atTime(LocalTime.MAX);

        DreResponseDTO dre = new DreResponseDTO();
        dre.setDataInicio(inicio);
        dre.setDataFim(fim);

        // ── (1) Faturamento ───────────────────────────────────────────────────
        List<Object[]> resumo = vendaRepository.resumoNoPeriodo(empresaId, ini, end);
        BigDecimal receitaLiquida = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;
        BigDecimal acrescimos = BigDecimal.ZERO;
        long qtdVendas = 0;
        if (!resumo.isEmpty() && resumo.get(0) != null) {
            Object[] linha = resumo.get(0);
            receitaLiquida = nvl((BigDecimal) linha[0]);
            descontos = nvl((BigDecimal) linha[1]);
            acrescimos = nvl((BigDecimal) linha[2]);
            qtdVendas = linha[3] != null ? ((Number) linha[3]).longValue() : 0L;
        }
        // valor_total já é (itens + acréscimo − desconto); a bruta desfaz a conta.
        BigDecimal receitaBruta = receitaLiquida.subtract(acrescimos).add(descontos);

        dre.setQuantidadeVendas(qtdVendas);
        dre.setReceitaBruta(esc2(receitaBruta));
        dre.setDescontos(esc2(descontos));
        dre.setAcrescimos(esc2(acrescimos));
        dre.setReceitaLiquida(esc2(receitaLiquida));

        // ── (2) CMV (competência) ─────────────────────────────────────────────
        BigDecimal cmv = nvl(custoVendaRepository.somarNoPeriodo(empresaId, inicio, fim));
        dre.setCmv(esc2(cmv));

        BigDecimal lucroBruto = receitaLiquida.subtract(cmv);
        dre.setLucroBruto(esc2(lucroBruto));
        dre.setMargemBruta(percentual(lucroBruto, receitaLiquida));

        // ── (3) Despesas operacionais (razão de caixa, tipo DESPESA) ──────────
        Map<Long, TipoClasse> tipoPorClasse = new HashMap<>();
        for (ClasseFinanceiraEntity c : classeRepository.findByEmpresaIdOrderByCodigoAscNomeAsc(empresaId))
            tipoPorClasse.put(c.getCodClasse(), c.getTipo());

        BigDecimal despesas = BigDecimal.ZERO;
        for (Object[] linha : lancamentoRepository.somarPorClasseNoPeriodo(empresaId, inicio, fim)) {
            if (tipoPorClasse.get((Long) linha[0]) == TipoClasse.DESPESA)
                despesas = despesas.add(nvl((BigDecimal) linha[1]));
        }
        dre.setDespesasOperacionais(esc2(despesas));

        BigDecimal resultado = lucroBruto.subtract(despesas);
        dre.setResultadoOperacional(esc2(resultado));
        dre.setMargemOperacional(percentual(resultado, receitaLiquida));
        dre.setTicketMedio(qtdVendas > 0
                ? receitaLiquida.divide(BigDecimal.valueOf(qtdVendas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // ── (4) Margem por produto ────────────────────────────────────────────
        dre.setProdutos(montarProdutos(empresaId, ini, end, inicio, fim, dre));

        // ── (5) Confiabilidade ────────────────────────────────────────────────
        dre.setVendasSemCmv(vendaRepository.contarSemCmvNoPeriodo(empresaId, ini, end));

        return dre;
    }

    private List<DreProdutoDTO> montarProdutos(Long empresaId,
                                               LocalDateTime ini, LocalDateTime end,
                                               LocalDate inicio, LocalDate fim,
                                               DreResponseDTO dre) {
        // Custo por produto
        Map<Long, BigDecimal> cmvPorProduto = new HashMap<>();
        for (Object[] linha : custoVendaRepository.somarPorProdutoNoPeriodo(empresaId, inicio, fim))
            cmvPorProduto.put((Long) linha[0], nvl((BigDecimal) linha[1]));

        List<DreProdutoDTO> produtos = new ArrayList<>();
        int semCusto = 0;
        BigDecimal receitaSemCusto = BigDecimal.ZERO;

        for (Object[] linha : produtoVendaRepository.somarPorProdutoNoPeriodo(empresaId, ini, end)) {
            Long codProduto = (Long) linha[0];
            BigDecimal quantidade = linha[2] != null
                    ? new BigDecimal(((Number) linha[2]).toString()) : BigDecimal.ZERO;
            BigDecimal receita = nvl((BigDecimal) linha[3]);
            BigDecimal custo = cmvPorProduto.getOrDefault(codProduto, BigDecimal.ZERO);
            BigDecimal lucro = receita.subtract(custo);

            boolean faltaCusto = custo.signum() <= 0 && receita.signum() > 0;
            if (faltaCusto) {
                semCusto++;
                receitaSemCusto = receitaSemCusto.add(receita);
            }

            produtos.add(new DreProdutoDTO(
                    codProduto,
                    (String) linha[1],
                    quantidade,
                    esc2(receita),
                    esc2(custo),
                    esc2(lucro),
                    percentual(lucro, receita),
                    faltaCusto));
        }

        produtos.sort((a, b) -> b.getLucroBruto().compareTo(a.getLucroBruto()));
        dre.setProdutosSemCusto(semCusto);
        dre.setReceitaSemCusto(esc2(receitaSemCusto));
        return produtos;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal esc2(BigDecimal v) {
        return nvl(v).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
