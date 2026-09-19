package com.horus.projeto.services;

import com.horus.projeto.entities.*;
import com.horus.projeto.enums.OrigemCusto;
import com.horus.projeto.repositories.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * CusteioService — fonte ÚNICA de verdade do custo unitário de um produto e
 * responsável por apurar o CMV (Custo da Mercadoria Vendida) de cada venda.
 *
 * Antes deste service o custo era calculado apenas dentro de ProdutoService.analiseLucro
 * (privado, só para ranking). Agora existe um só motor: o número que aparece no ranking
 * de margem é exatamente o número que vai para o CMV e para a DRE.
 *
 * Regime: o CMV é COMPETÊNCIA (data da venda) e vive em custo_venda — nunca no razão
 * de caixa (lancamento_financeiro), para não duplicar a saída já registrada na compra.
 */
@Service
@RequiredArgsConstructor
public class CusteioService {

    private static final Logger log = LoggerFactory.getLogger(CusteioService.class);

    /** Casas decimais do custo unitário — BOM de insumo costuma ter custo fracionário. */
    private static final int ESCALA_CUSTO = 4;

    private final CustoVendaRepository custoVendaRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoMateriaPrimaRepository materiaPrimaRepository;
    private final VendaRepository vendaRepository;
    private final EmpresaRepository empresaRepository;
    private final ParametrosFinanceiroService parametrosService;

    // ═══════════════════════════════════════════════════════════════════════
    // TABELA DE CUSTOS — carrega produtos e composições da empresa UMA vez
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Carrega o universo de produtos/composições da empresa e devolve um resolvedor
     * de custo memoizado. Reutilize a mesma tabela em operações de lote (reprocessamento,
     * ranking de margem) — evita N+1 na explosão recursiva da composição.
     */
    public TabelaCustos carregarTabela(Long empresaId) {
        List<ProdutoEntity> produtos = produtoRepository.findByEmpresaId(empresaId);
        List<ProdutoMateriaPrimaEntity> composicoes = materiaPrimaRepository.findAllByEmpresaId(empresaId);

        Map<Long, ProdutoEntity> porId = new HashMap<>();
        for (ProdutoEntity p : produtos) porId.put(p.getCodProduto(), p);

        Map<Long, List<ProdutoMateriaPrimaEntity>> porPai = new HashMap<>();
        for (ProdutoMateriaPrimaEntity c : composicoes)
            porPai.computeIfAbsent(c.getId().getCodProdutoFinal(), k -> new ArrayList<>()).add(c);

        return new TabelaCustos(porId, porPai);
    }

    /** Custo unitário de um único produto (atalho — carrega a tabela inteira). */
    public BigDecimal custoUnitario(Long codProduto, Long empresaId) {
        return carregarTabela(empresaId).custoUnitario(codProduto);
    }

    /**
     * Resolvedor de custo unitário com memória. Regra:
     *  - produto COM composição  -> soma recursiva (custo do insumo × quantidade)
     *  - produto SEM composição  -> valor_custo cadastrado (0 se ausente)
     * O Set de visitados (clonado por ramo) impede loop em composição circular.
     */
    public static final class TabelaCustos {
        private final Map<Long, ProdutoEntity> produtos;
        private final Map<Long, List<ProdutoMateriaPrimaEntity>> composicoes;
        private final Map<Long, BigDecimal> memo = new HashMap<>();

        private TabelaCustos(Map<Long, ProdutoEntity> produtos,
                             Map<Long, List<ProdutoMateriaPrimaEntity>> composicoes) {
            this.produtos = produtos;
            this.composicoes = composicoes;
        }

        public BigDecimal custoUnitario(Long codProduto) {
            if (codProduto == null) return BigDecimal.ZERO.setScale(ESCALA_CUSTO);
            return resolver(codProduto, new HashSet<>());
        }

        public boolean possuiComposicao(Long codProduto) {
            return !composicoes.getOrDefault(codProduto, List.of()).isEmpty();
        }

        public ProdutoEntity produto(Long codProduto) {
            return produtos.get(codProduto);
        }

        public Collection<ProdutoEntity> todosProdutos() {
            return produtos.values();
        }

        private BigDecimal resolver(Long codProduto, Set<Long> visitados) {
            BigDecimal memoizado = memo.get(codProduto);
            if (memoizado != null) return memoizado;
            if (!visitados.add(codProduto)) return BigDecimal.ZERO.setScale(ESCALA_CUSTO); // ciclo

            ProdutoEntity produto = produtos.get(codProduto);
            if (produto == null) return BigDecimal.ZERO.setScale(ESCALA_CUSTO);

            List<ProdutoMateriaPrimaEntity> comps = composicoes.getOrDefault(codProduto, List.of());
            BigDecimal custo;
            if (comps.isEmpty()) {
                custo = produto.getValorCusto() != null ? produto.getValorCusto() : BigDecimal.ZERO;
            } else {
                custo = BigDecimal.ZERO;
                for (ProdutoMateriaPrimaEntity comp : comps) {
                    BigDecimal qtd = comp.getQuantidade() != null ? comp.getQuantidade() : BigDecimal.ONE;
                    BigDecimal custoInsumo = resolver(comp.getId().getCodProdutoMateriaPrima(),
                            new HashSet<>(visitados));
                    custo = custo.add(custoInsumo.multiply(qtd));
                }
            }
            if (custo.signum() < 0) custo = BigDecimal.ZERO;
            custo = custo.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
            memo.put(codProduto, custo);
            return custo;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // APURAÇÃO DO CMV
    // ═══════════════════════════════════════════════════════════════════════

    /** Apura o CMV de uma venda carregando a tabela de custos sob demanda. */
    @Transactional
    public int registrarCustoVenda(VendaEntity venda, Long empresaId) {
        return registrarCustoVenda(venda, empresaId, carregarTabela(empresaId));
    }

    /**
     * Apura e grava o CMV de uma venda (uma linha por item), congelando o custo unitário.
     *
     * Nunca lança exceção por motivo de negócio — a ausência de custo NÃO pode derrubar
     * uma venda. Item sem base de custo é gravado com custo 0 e origem SEM_CUSTO, o que
     * o torna visível no diagnóstico em vez de sumir silenciosamente.
     *
     * Idempotente: se a venda já possui CMV ativo, não faz nada.
     *
     * @return quantidade de linhas de CMV gravadas
     */
    @Transactional
    public int registrarCustoVenda(VendaEntity venda, Long empresaId, TabelaCustos tabela) {
        if (venda == null || venda.getCodVenda() == null) return 0;
        if (Boolean.TRUE.equals(venda.getEstornada())) return 0;
        if (venda.getItens() == null || venda.getItens().isEmpty()) return 0;
        if (custoVendaRepository.existsByCodVendaAndEstornadoFalse(venda.getCodVenda())) return 0;

        Long codClasseCmv = parametrosService.getClasseCmv(empresaId);
        LocalDate dataCompetencia = venda.getDataVenda() != null
                ? venda.getDataVenda().toLocalDate()
                : LocalDate.now();
        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);

        int gravadas = 0;
        for (ProdutoVendaEntity item : venda.getItens()) {
            Long codProduto = item.getProduto() != null ? item.getProduto().getCodProduto() : null;
            if (codProduto == null) continue;

            BigDecimal quantidade = item.getQuantidade() != null
                    ? new BigDecimal(item.getQuantidade()) : BigDecimal.ZERO;
            if (quantidade.signum() <= 0) continue;

            BigDecimal unitario = tabela.custoUnitario(codProduto);
            BigDecimal total = unitario.multiply(quantidade).setScale(2, RoundingMode.HALF_UP);

            ProdutoEntity produtoRef = tabela.produto(codProduto);
            String nome = produtoRef != null ? produtoRef.getNome()
                    : (item.getProduto() != null ? item.getProduto().getNome() : null);

            CustoVendaEntity custo = new CustoVendaEntity();
            custo.setEmpresa(empresa);
            custo.setCodVenda(venda.getCodVenda());
            custo.setCodItemVenda(item.getCodItemVenda());
            custo.setCodProduto(codProduto);
            custo.setNomeProduto(nome);
            custo.setCodClasse(codClasseCmv);
            custo.setQuantidade(quantidade.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP));
            custo.setCustoUnitario(unitario);
            custo.setCustoTotal(total);
            custo.setDataMovimento(dataCompetencia);
            custo.setOrigemCusto(classificarOrigem(unitario, tabela.possuiComposicao(codProduto)));
            custo.setEstornado(false);

            custoVendaRepository.save(custo);
            gravadas++;
        }
        return gravadas;
    }

    private OrigemCusto classificarOrigem(BigDecimal unitario, boolean possuiComposicao) {
        if (unitario == null || unitario.signum() <= 0) return OrigemCusto.SEM_CUSTO;
        return possuiComposicao ? OrigemCusto.COMPOSICAO : OrigemCusto.CADASTRO;
    }

    /** Estorna o CMV de uma venda estornada (mesma semântica append-only do razão). */
    @Transactional
    public int estornarPorVenda(Long codVenda) {
        if (codVenda == null) return 0;
        List<CustoVendaEntity> linhas = custoVendaRepository.findByCodVendaAndEstornadoFalse(codVenda);
        for (CustoVendaEntity linha : linhas) linha.setEstornado(true);
        custoVendaRepository.saveAll(linhas);
        return linhas.size();
    }

    /**
     * Backfill: apura o CMV das vendas que ainda não o têm (anteriores a este módulo).
     * Idempotente e restrito a vendas não estornadas.
     *
     * LIMITAÇÃO CONHECIDA: como não existe histórico de custo, o backfill usa o custo
     * ATUAL do produto. Vendas antigas ficam com o custo de hoje. Vendas novas, a partir
     * de agora, congelam o custo correto no ato.
     *
     * @return quantidade de vendas reprocessadas
     */
    @Transactional
    public int reprocessar(Long empresaId) {
        TabelaCustos tabela = carregarTabela(empresaId);
        List<VendaEntity> vendas = vendaRepository.findByEmpresaId(
                empresaId, Sort.by(Sort.Direction.ASC, "dataVenda"));

        int reprocessadas = 0;
        for (VendaEntity venda : vendas) {
            if (Boolean.TRUE.equals(venda.getEstornada())) continue;
            if (custoVendaRepository.existsByCodVendaAndEstornadoFalse(venda.getCodVenda())) continue;
            if (registrarCustoVenda(venda, empresaId, tabela) > 0) reprocessadas++;
        }
        log.info("CMV reprocessado para a empresa {}: {} venda(s).", empresaId, reprocessadas);
        return reprocessadas;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIAGNÓSTICO — o que impede a margem de ser confiável
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Aponta os buracos de custeio da empresa:
     *  - produtos JÁ VENDIDOS sem nenhuma base de custo (distorcem a margem para cima)
     *  - produtos cadastrados sem custo e sem composição (vão distorcer na próxima venda)
     *  - se a classe analítica de CMV está configurada
     */
    public Map<String, Object> diagnostico(Long empresaId) {
        List<Map<String, Object>> vendidosSemCusto = new ArrayList<>();
        for (Object[] linha : custoVendaRepository.produtosVendidosSemCusto(empresaId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("codProduto", linha[0]);
            item.put("nome", linha[1]);
            item.put("quantidadeVendida", linha[2]);
            item.put("ocorrencias", linha[3]);
            vendidosSemCusto.add(item);
        }

        TabelaCustos tabela = carregarTabela(empresaId);
        List<Map<String, Object>> cadastroSemCusto = new ArrayList<>();
        for (ProdutoEntity p : tabela.todosProdutos()) {
            if (tabela.custoUnitario(p.getCodProduto()).signum() > 0) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("codProduto", p.getCodProduto());
            item.put("nome", p.getNome());
            item.put("tipo", p.getTipo() != null ? p.getTipo().name() : null);
            item.put("possuiComposicao", tabela.possuiComposicao(p.getCodProduto()));
            cadastroSemCusto.add(item);
        }

        Long codClasseCmv = parametrosService.getClasseCmv(empresaId);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("classeCmvConfigurada", codClasseCmv != null);
        resposta.put("codClasseCmv", codClasseCmv);
        resposta.put("linhasCmvAtivas", custoVendaRepository.countByEmpresaIdAndEstornadoFalse(empresaId));
        resposta.put("produtosVendidosSemCusto", vendidosSemCusto);
        resposta.put("produtosCadastradosSemCusto", cadastroSemCusto);
        return resposta;
    }
}
