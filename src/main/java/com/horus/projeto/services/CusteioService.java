package com.horus.projeto.services;

import com.horus.projeto.entities.*;
import com.horus.projeto.enums.OrigemCusto;
import com.horus.projeto.enums.OrigemEntradaEstoque;
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

    /** Casas decimais de QUANTIDADE (estoque, entradas, consumo). */
    public static final int ESCALA_QUANTIDADE = 3;

    private final CustoVendaRepository custoVendaRepository;
    private final ProdutoCustoHistoricoRepository historicoRepository;
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
     * Resolvedor de custo unitário com memória. Cascata de resolução:
     *  1. custo_medio  -> custo médio ponderado móvel (autoritativo: é o custo real
     *                     do que está em estoque, acumulado pelas entradas)
     *  2. composição   -> soma recursiva (custo do insumo × quantidade); estimativa
     *                     para produto que ainda não foi produzido nenhuma vez
     *  3. valor_custo  -> custo informado manualmente (0 se ausente)
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

        /** De onde veio o custo deste produto — grava no CMV para auditoria. */
        public OrigemCusto origemDoCusto(Long codProduto) {
            if (custoUnitario(codProduto).signum() <= 0) return OrigemCusto.SEM_CUSTO;
            ProdutoEntity produto = produtos.get(codProduto);
            if (produto != null && produto.getCustoMedio() != null
                    && produto.getCustoMedio().signum() > 0) return OrigemCusto.CUSTO_MEDIO;
            return possuiComposicao(codProduto) ? OrigemCusto.COMPOSICAO : OrigemCusto.CADASTRO;
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

            BigDecimal custo;
            BigDecimal medio = produto.getCustoMedio();
            if (medio != null && medio.signum() > 0) {
                // (1) custo médio ponderado: o custo real do que está em estoque
                custo = medio;
            } else {
                List<ProdutoMateriaPrimaEntity> comps = composicoes.getOrDefault(codProduto, List.of());
                if (comps.isEmpty()) {
                    // (3) custo informado manualmente
                    custo = produto.getValorCusto() != null ? produto.getValorCusto() : BigDecimal.ZERO;
                } else {
                    // (2) estimativa pela composição — produto ainda não produzido
                    custo = BigDecimal.ZERO;
                    for (ProdutoMateriaPrimaEntity comp : comps) {
                        BigDecimal qtd = comp.getQuantidade() != null ? comp.getQuantidade() : BigDecimal.ONE;
                        BigDecimal custoInsumo = resolver(comp.getId().getCodProdutoMateriaPrima(),
                                new HashSet<>(visitados));
                        custo = custo.add(custoInsumo.multiply(qtd));
                    }
                }
            }
            if (custo.signum() < 0) custo = BigDecimal.ZERO;
            custo = custo.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
            memo.put(codProduto, custo);
            return custo;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CUSTO MÉDIO PONDERADO MÓVEL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Registra uma ENTRADA de estoque e recalcula o custo médio ponderado móvel.
     *
     *   custoNovo = (qtdAnterior × custoAnterior + qtdEntrada × custoEntrada)
     *               ÷ (qtdAnterior + qtdEntrada)
     *
     * Este método é o ÚNICO lugar do sistema que incrementa estoque — ele soma a
     * quantidade E atualiza o custo na mesma operação. Chamar `setQuantidadeEstoque`
     * direto para dar entrada deixa o custo desatualizado silenciosamente.
     *
     * Saídas (venda, consumo em produção) continuam decrementando direto: saída não
     * altera custo médio, só quantidade. Essa assimetria é a própria definição do método.
     *
     * Bootstrap: quando não há estoque anterior (qtd ≤ 0) ou não há custo anterior
     * (custo ≤ 0), o custo da entrada passa a valer para o todo. Sem isso, um produto
     * com estoque histórico e custo zero diluiria a primeira compra pela metade.
     *
     * @return o produto já salvo, com estoque e custo atualizados
     */
    @Transactional
    public ProdutoEntity registrarEntrada(ProdutoEntity produto, Long empresaId,
                                          BigDecimal quantidade, BigDecimal custoUnitarioEntrada,
                                          OrigemEntradaEstoque origem, Long origemId,
                                          LocalDate data, String descricao) {
        if (produto == null) return null;
        if (quantidade == null || quantidade.signum() <= 0) return produto;

        BigDecimal qtdAnterior = produto.getQuantidadeEstoque() != null
                ? produto.getQuantidadeEstoque() : BigDecimal.ZERO;
        BigDecimal custoAnterior = produto.getCustoMedio() != null
                ? produto.getCustoMedio() : BigDecimal.ZERO;
        BigDecimal custoEntrada = custoUnitarioEntrada != null && custoUnitarioEntrada.signum() > 0
                ? custoUnitarioEntrada : BigDecimal.ZERO;

        BigDecimal qtdNova = qtdAnterior.add(quantidade);

        BigDecimal custoNovo;
        if (qtdAnterior.signum() <= 0 || custoAnterior.signum() <= 0) {
            custoNovo = custoEntrada;
        } else {
            BigDecimal valorAnterior = qtdAnterior.multiply(custoAnterior);
            BigDecimal valorEntrada = quantidade.multiply(custoEntrada);
            custoNovo = valorAnterior.add(valorEntrada)
                    .divide(qtdNova, ESCALA_CUSTO, RoundingMode.HALF_UP);
        }
        custoNovo = custoNovo.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);

        qtdNova = qtdNova.setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
        produto.setQuantidadeEstoque(qtdNova);
        produto.setCustoMedio(custoNovo);
        ProdutoEntity salvo = produtoRepository.save(produto);

        ProdutoCustoHistoricoEntity h = new ProdutoCustoHistoricoEntity();
        h.setEmpresa(empresaRepository.getReferenceById(empresaId));
        h.setCodProduto(produto.getCodProduto());
        h.setQuantidadeAnterior(escQtd(qtdAnterior));
        h.setCustoAnterior(esc(custoAnterior));
        h.setQuantidadeEntrada(escQtd(quantidade));
        h.setCustoEntrada(esc(custoEntrada));
        h.setQuantidadeNova(escQtd(qtdNova));
        h.setCustoNovo(esc(custoNovo));
        h.setOrigem(origem);
        h.setOrigemId(origemId);
        h.setDescricao(descricao);
        h.setDataMovimento(data != null ? data : LocalDate.now());
        historicoRepository.save(h);

        return salvo;
    }

    /**
     * Define o custo médio manualmente (usuário digitou o Valor de Custo no cadastro).
     * Não mexe no estoque — apenas reavalia o custo e deixa rastro no histórico.
     */
    @Transactional
    public void definirCustoManual(ProdutoEntity produto, Long empresaId, BigDecimal novoCusto) {
        if (produto == null || novoCusto == null || novoCusto.signum() < 0) return;
        BigDecimal custoAnterior = produto.getCustoMedio() != null ? produto.getCustoMedio() : BigDecimal.ZERO;
        BigDecimal alvo = novoCusto.setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
        if (custoAnterior.compareTo(alvo) == 0) return;

        BigDecimal qtd = produto.getQuantidadeEstoque() != null
                ? produto.getQuantidadeEstoque() : BigDecimal.ZERO;
        produto.setCustoMedio(alvo);

        ProdutoCustoHistoricoEntity h = new ProdutoCustoHistoricoEntity();
        h.setEmpresa(empresaRepository.getReferenceById(empresaId));
        h.setCodProduto(produto.getCodProduto());
        h.setQuantidadeAnterior(escQtd(qtd));
        h.setCustoAnterior(esc(custoAnterior));
        h.setQuantidadeEntrada(BigDecimal.ZERO.setScale(ESCALA_QUANTIDADE));
        h.setCustoEntrada(alvo);
        h.setQuantidadeNova(escQtd(qtd));
        h.setCustoNovo(alvo);
        h.setOrigem(OrigemEntradaEstoque.AJUSTE_MANUAL);
        h.setDescricao("Custo informado manualmente no cadastro do produto");
        h.setDataMovimento(LocalDate.now());
        historicoRepository.save(h);
    }

    /** Trilha de auditoria do custo de um produto (mais recente primeiro). */
    public List<ProdutoCustoHistoricoEntity> historicoCusto(Long empresaId, Long codProduto) {
        return historicoRepository.findByEmpresaIdAndCodProdutoOrderByCodHistoricoDesc(empresaId, codProduto);
    }

    /**
     * Custo unitário com que cada produto SAIU numa venda (snapshot do CMV).
     * Usado no estorno: o item tem que voltar ao estoque pelo custo com que saiu,
     * senão o estorno contamina o custo médio com um valor inventado.
     */
    public Map<Long, BigDecimal> custosDaVenda(Long codVenda) {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (CustoVendaEntity linha : custoVendaRepository.findByCodVendaAndEstornadoFalse(codVenda))
            mapa.putIfAbsent(linha.getCodProduto(), linha.getCustoUnitario());
        return mapa;
    }

    private static BigDecimal esc(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(ESCALA_CUSTO, RoundingMode.HALF_UP);
    }

    private static BigDecimal escQtd(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(ESCALA_QUANTIDADE, RoundingMode.HALF_UP);
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
                    ? item.getQuantidade() : BigDecimal.ZERO;
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
            custo.setQuantidade(escQtd(quantidade));
            custo.setCustoUnitario(unitario);
            custo.setCustoTotal(total);
            custo.setDataMovimento(dataCompetencia);
            custo.setOrigemCusto(tabela.origemDoCusto(codProduto));
            custo.setEstornado(false);

            custoVendaRepository.save(custo);
            gravadas++;
        }
        return gravadas;
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
