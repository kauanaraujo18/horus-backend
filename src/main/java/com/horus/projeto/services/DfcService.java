package com.horus.projeto.services;

import com.horus.projeto.dto.*;
import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.enums.TipoClasse;
import com.horus.projeto.repositories.ClasseFinanceiraRepository;
import com.horus.projeto.repositories.ContaFinanceiraRepository;
import com.horus.projeto.repositories.LancamentoFinanceiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Motor do DFC v2 — colunas dinâmicas por mês + consolidado, com rodapé de variação.
 * Mesma estratégia em memória: árvore montada uma vez, valores propagados das folhas
 * (analíticas) para os totalizadores (sintéticas), agora por mês.
 */
@Service
@RequiredArgsConstructor
public class DfcService {

    private static final String[] MESES_ABREV =
            {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};

    private final ClasseFinanceiraRepository classeRepo;
    private final LancamentoFinanceiroRepository lancamentoRepo;
    private final ContaFinanceiraRepository contaRepo;

    public DfcResponseDTO gerar(Long empresaId, LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null)
            throw new IllegalArgumentException("Período (início e fim) é obrigatório.");
        if (inicio.isAfter(fim))
            throw new IllegalArgumentException("A data inicial não pode ser maior que a final.");

        List<ClasseFinanceiraEntity> classes = classeRepo.findByEmpresaIdOrderByCodigoAscNomeAsc(empresaId);
        Map<Long, TipoClasse> tipoPorClasse = classes.stream()
                .collect(Collectors.toMap(ClasseFinanceiraEntity::getCodClasse, ClasseFinanceiraEntity::getTipo));

        // ── (1) meses do período e somas por classe em cada mês ───────────────
        List<YearMonth> ym = mesesDoPeriodo(inicio, fim);
        List<Map<Long, BigDecimal>> somasPorMes = new ArrayList<>();
        for (YearMonth m : ym) {
            LocalDate mIni = maxData(inicio, m.atDay(1));
            LocalDate mFim = minData(fim, m.atEndOfMonth());
            Map<Long, BigDecimal> mapa = new HashMap<>();
            for (Object[] linha : lancamentoRepo.somarPorClasseNoPeriodo(empresaId, mIni, mFim)) {
                mapa.put((Long) linha[0], (BigDecimal) linha[1]);
            }
            somasPorMes.add(mapa);
        }
        // consolidado = soma dos meses
        Map<Long, BigDecimal> somaConsolidada = new HashMap<>();
        for (Map<Long, BigDecimal> mm : somasPorMes)
            mm.forEach((k, v) -> somaConsolidada.merge(k, v, BigDecimal::add));

        // ── (2) árvore (consolidado + por mês) ────────────────────────────────
        Map<Long, List<ClasseFinanceiraEntity>> filhosPorPai = classes.stream()
                .filter(c -> c.getCodClassePai() != null)
                .collect(Collectors.groupingBy(ClasseFinanceiraEntity::getCodClassePai));

        DfcResponseDTO resp = new DfcResponseDTO();
        resp.setDataInicio(inicio);
        resp.setDataFim(fim);
        resp.setMeses(ym.stream().map(this::toMesDTO).collect(Collectors.toList()));

        for (ClasseFinanceiraEntity raiz : classes.stream().filter(c -> c.getCodClassePai() == null).toList()) {
            DfcNodoDTO no = montarNo(raiz, filhosPorPai, somaConsolidada, somasPorMes);
            switch (raiz.getTipo()) {
                case RECEITA -> resp.getReceitas().add(no);
                case CUSTO   -> resp.getCustos().add(no);
                case DESPESA -> resp.getDespesas().add(no);
            }
        }
        resp.setTotalReceitas(somarRaizes(resp.getReceitas()));
        resp.setTotalCustos(somarRaizes(resp.getCustos()));
        resp.setTotalDespesas(somarRaizes(resp.getDespesas()));
        resp.setResultadoCaixa(resp.getTotalReceitas()
                .subtract(resp.getTotalCustos()).subtract(resp.getTotalDespesas()));

        // ── (3) rodapé de variação ────────────────────────────────────────────
        resp.setVariacao(montarVariacao(empresaId, inicio, somasPorMes, tipoPorClasse));
        return resp;
    }

    /** Série de evolução do saldo da empresa (somatório das contas) ao longo do período. */
    public List<SaldoPontoDTO> evolucaoSaldo(Long empresaId, LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null)
            throw new IllegalArgumentException("Período (início e fim) é obrigatório.");
        if (inicio.isAfter(fim))
            throw new IllegalArgumentException("A data inicial não pode ser maior que a final.");

        BigDecimal saldo = nvl(contaRepo.somarSaldoInicial(empresaId))
                .add(nvl(lancamentoRepo.saldoAssinadoAntesDe(empresaId, inicio)));

        List<SaldoPontoDTO> serie = new ArrayList<>();
        serie.add(new SaldoPontoDTO(inicio, saldo)); // ponto de partida (saldo de abertura)
        for (Object[] linha : lancamentoRepo.somarAssinadoPorDia(empresaId, inicio, fim)) {
            LocalDate dia = (LocalDate) linha[0];
            saldo = saldo.add((BigDecimal) linha[1]);
            serie.add(new SaldoPontoDTO(dia, saldo));
        }
        return serie;
    }

    // ── Árvore ────────────────────────────────────────────────────────────────

    private DfcNodoDTO montarNo(ClasseFinanceiraEntity classe,
                                Map<Long, List<ClasseFinanceiraEntity>> filhosPorPai,
                                Map<Long, BigDecimal> somaConsolidada,
                                List<Map<Long, BigDecimal>> somasPorMes) {
        DfcNodoDTO no = new DfcNodoDTO();
        no.setCodClasse(classe.getCodClasse());
        no.setCodigo(classe.getCodigo());
        no.setNome(classe.getNome());
        no.setTipo(classe.getTipo().name());
        no.setNivel(classe.getNivel().name());

        int nMeses = somasPorMes.size();

        if (classe.getNivel() == NivelClasse.ANALITICA) {
            no.setValor(somaConsolidada.getOrDefault(classe.getCodClasse(), BigDecimal.ZERO));
            List<BigDecimal> porMes = new ArrayList<>(nMeses);
            for (Map<Long, BigDecimal> mm : somasPorMes)
                porMes.add(mm.getOrDefault(classe.getCodClasse(), BigDecimal.ZERO));
            no.setValoresPorMes(porMes);
            no.setFilhos(List.of());
        } else {
            List<DfcNodoDTO> filhos = filhosPorPai.getOrDefault(classe.getCodClasse(), List.of())
                    .stream().map(f -> montarNo(f, filhosPorPai, somaConsolidada, somasPorMes))
                    .collect(Collectors.toList());
            no.setFilhos(filhos);
            no.setValor(filhos.stream().map(DfcNodoDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
            List<BigDecimal> porMes = new ArrayList<>(Collections.nCopies(nMeses, BigDecimal.ZERO));
            for (DfcNodoDTO filho : filhos)
                for (int i = 0; i < nMeses; i++)
                    porMes.set(i, porMes.get(i).add(filho.getValoresPorMes().get(i)));
            no.setValoresPorMes(porMes);
        }
        return no;
    }

    // ── Rodapé: Saldo Inicial + Entradas − Custos − Despesas = Saldo Final ────

    private DfcVariacaoDTO montarVariacao(Long empresaId, LocalDate inicio,
                                          List<Map<Long, BigDecimal>> somasPorMes,
                                          Map<Long, TipoClasse> tipoPorClasse) {
        DfcVariacaoDTO v = new DfcVariacaoDTO();

        // Saldo das contas no início do período = abertura + razão antes de 'inicio'
        BigDecimal saldoInicial = nvl(contaRepo.somarSaldoInicial(empresaId))
                .add(nvl(lancamentoRepo.saldoAssinadoAntesDe(empresaId, inicio)));

        BigDecimal saldoCorrente = saldoInicial;
        BigDecimal totE = BigDecimal.ZERO, totC = BigDecimal.ZERO, totD = BigDecimal.ZERO;

        for (Map<Long, BigDecimal> mes : somasPorMes) {
            BigDecimal entradas = BigDecimal.ZERO, custos = BigDecimal.ZERO, despesas = BigDecimal.ZERO;
            for (Map.Entry<Long, BigDecimal> e : mes.entrySet()) {
                TipoClasse tipo = tipoPorClasse.get(e.getKey());
                if (tipo == TipoClasse.RECEITA) entradas = entradas.add(e.getValue());
                else if (tipo == TipoClasse.CUSTO) custos = custos.add(e.getValue());
                else if (tipo == TipoClasse.DESPESA) despesas = despesas.add(e.getValue());
            }
            BigDecimal saldoFinalMes = saldoCorrente.add(entradas).subtract(custos).subtract(despesas);

            v.getSaldoInicialMes().add(saldoCorrente);
            v.getEntradasMes().add(entradas);
            v.getCustosMes().add(custos);
            v.getDespesasMes().add(despesas);
            v.getSaldoFinalMes().add(saldoFinalMes);

            totE = totE.add(entradas); totC = totC.add(custos); totD = totD.add(despesas);
            saldoCorrente = saldoFinalMes; // encadeia
        }

        v.setSaldoInicial(saldoInicial);
        v.setTotalEntradas(totE);
        v.setTotalCustos(totC);
        v.setTotalDespesas(totD);
        v.setSaldoFinal(saldoInicial.add(totE).subtract(totC).subtract(totD));
        return v;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<YearMonth> mesesDoPeriodo(LocalDate inicio, LocalDate fim) {
        List<YearMonth> lista = new ArrayList<>();
        YearMonth cursor = YearMonth.from(inicio);
        YearMonth ultimo = YearMonth.from(fim);
        while (!cursor.isAfter(ultimo)) { lista.add(cursor); cursor = cursor.plusMonths(1); }
        return lista;
    }

    private DfcMesDTO toMesDTO(YearMonth m) {
        return new DfcMesDTO(m.toString(), MESES_ABREV[m.getMonthValue() - 1] + "/" + (m.getYear() % 100));
    }

    private BigDecimal somarRaizes(List<DfcNodoDTO> nos) {
        return nos.stream().map(DfcNodoDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate maxData(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }
    private LocalDate minData(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }
    private BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
