package com.horus.projeto.services;

import com.horus.projeto.dto.ProducaoCalculoDTO;
import com.horus.projeto.dto.ProducaoCalculoDTO.InsumoNecessarioDTO;
import com.horus.projeto.entities.*;
import com.horus.projeto.enums.OrigemEntradaEstoque;
import com.horus.projeto.enums.TipoProduto;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.ProdutoMateriaPrimaRepository;
import com.horus.projeto.repositories.ProdutoRepository;
import com.horus.projeto.repositories.ProducaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * ProducaoService — gerencia o ciclo de vida das ordens de produção.
 *
 * Fluxo de produção:
 *  1. Calcular   — preview dos insumos necessários (sem alterar nada).
 *  2. Realizar   — debita MPs recursivamente, credita PF/MPPF, salva registro.
 *  3. Estornar   — reverte a produção, devolvendo insumos e retirando PF/MPPF.
 *
 * Tipos suportados como produto a produzir: PF e MPPF.
 * Insumos consumidos: apenas MPs folha (resolução recursiva via MPPF intermediários).
 */
@Service
@RequiredArgsConstructor
public class ProducaoService {

    private final ProducaoRepository producaoRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoMateriaPrimaRepository materiaPrimaRepository;
    private final EmpresaRepository empresaRepository;
    private final CusteioService custeioService;

    // ═══════════════════════════════════════════════════════════════════════
    // CONSULTA
    // ═══════════════════════════════════════════════════════════════════════

    public List<ProducaoEntity> listarPorEmpresa(Long empresaId) {
        return producaoRepository.findByEmpresaIdOrderByDataProducaoDesc(empresaId);
    }

    public ProducaoEntity buscarPorId(Long id, Long empresaId) {
        return producaoRepository.findByCodProducaoAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RuntimeException(
                        "Acesso Negado: Produção não encontrada ou não pertence a esta empresa."));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CALCULAR (preview — não altera nenhum estoque)
    // ═══════════════════════════════════════════════════════════════════════

    public ProducaoCalculoDTO calcular(Long codProduto, BigDecimal quantidade, Long empresaId) {
        ProdutoEntity produto = buscarProdutoValidado(codProduto, empresaId);
        if (quantidade == null || quantidade.signum() <= 0)
            throw new IllegalArgumentException("A quantidade a produzir deve ser maior que zero.");

        Map<Long, BigDecimal> mpNecessarios = new LinkedHashMap<>();
        resolverInsumos(produto, quantidade, mpNecessarios, new HashSet<>());

        if (mpNecessarios.isEmpty())
            throw new IllegalArgumentException(
                    "O produto '" + produto.getNome() + "' não possui composição (insumos) cadastrada. " +
                    "Cadastre os insumos na tela de Produtos antes de produzir.");

        boolean podeRealizar = true;
        List<InsumoNecessarioDTO> insumos = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : mpNecessarios.entrySet()) {
            ProdutoEntity insumo = produtoRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Insumo ID " + entry.getKey() + " não encontrado."));
            BigDecimal necessaria = entry.getValue();
            BigDecimal disponivel = nvl(insumo.getQuantidadeEstoque());
            boolean suficiente = disponivel.compareTo(necessaria) >= 0;
            if (!suficiente) podeRealizar = false;

            insumos.add(new InsumoNecessarioDTO(
                    insumo.getCodProduto(),
                    insumo.getNome(),
                    necessaria,
                    disponivel,
                    suficiente));
        }

        return new ProducaoCalculoDTO(
                produto.getCodProduto(),
                produto.getNome(),
                produto.getTipo().name(),
                quantidade,
                podeRealizar,
                insumos);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REALIZAR PRODUÇÃO
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public ProducaoEntity realizarProducao(Long codProduto, BigDecimal quantidade, Long empresaId) {
        ProdutoEntity produto = buscarProdutoValidado(codProduto, empresaId);
        if (quantidade == null || quantidade.signum() <= 0)
            throw new IllegalArgumentException("A quantidade a produzir deve ser maior que zero.");

        // Resolve todos os MPs folha necessários
        Map<Long, BigDecimal> mpNecessarios = new LinkedHashMap<>();
        resolverInsumos(produto, quantidade, mpNecessarios, new HashSet<>());

        if (mpNecessarios.isEmpty())
            throw new IllegalArgumentException(
                    "O produto '" + produto.getNome() + "' não possui composição cadastrada.");

        // ── Passa 1: valida estoque de TODOS os insumos antes de debitar qualquer um ──
        List<ProdutoEntity> insumosCachados = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : mpNecessarios.entrySet()) {
            ProdutoEntity insumo = produtoRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("Insumo ID " + entry.getKey() + " não encontrado."));
            BigDecimal disponivel = nvl(insumo.getQuantidadeEstoque());
            BigDecimal necessaria = entry.getValue();
            if (disponivel.compareTo(necessaria) < 0) {
                throw new RuntimeException(String.format(
                        "Estoque insuficiente do insumo '%s'. Disponível: %s | Necessário: %s",
                        insumo.getNome(), fmtQtd(disponivel), fmtQtd(necessaria)));
            }
            insumosCachados.add(insumo);
        }

        // ── Passa 2: debita insumos, constrói snapshot e acumula o custo real ──
        ProducaoEntity producao = new ProducaoEntity();
        producao.setProduto(produto);
        producao.setQuantidadeProduzida(quantidade);
        producao.setEmpresa(empresaRepository.getReferenceById(empresaId));

        // Tabela de custos lida ANTES de qualquer baixa — é o custo vigente do insumo.
        CusteioService.TabelaCustos tabela = custeioService.carregarTabela(empresaId);
        BigDecimal custoTotalProducao = BigDecimal.ZERO;

        int i = 0;
        for (Map.Entry<Long, BigDecimal> entry : mpNecessarios.entrySet()) {
            ProdutoEntity insumo = insumosCachados.get(i++);
            BigDecimal necessaria = entry.getValue();

            // Consumo é SAÍDA: reduz quantidade e não altera o custo médio do insumo.
            // A quantidade entra INTEIRA (0,350 kg continua 0,350 kg) — era aqui que
            // o .intValue() zerava o consumo fracionário e corroía o estoque.
            insumo.setQuantidadeEstoque(nvl(insumo.getQuantidadeEstoque())
                    .subtract(necessaria)
                    .setScale(CusteioService.ESCALA_QUANTIDADE, RoundingMode.HALF_UP));
            produtoRepository.save(insumo);

            BigDecimal custoInsumo = tabela.custoUnitario(insumo.getCodProduto());
            custoTotalProducao = custoTotalProducao.add(custoInsumo.multiply(necessaria));

            ProducaoItemEntity itemConsumido = new ProducaoItemEntity();
            itemConsumido.setProducao(producao);
            itemConsumido.setInsumo(insumo);
            itemConsumido.setQuantidadeConsumida(necessaria);
            itemConsumido.setCustoUnitario(custoInsumo);
            producao.getItensConsumidos().add(itemConsumido);
        }

        custoTotalProducao = custoTotalProducao.setScale(2, RoundingMode.HALF_UP);
        BigDecimal custoUnitarioProduzido = custoTotalProducao
                .divide(quantidade, 4, RoundingMode.HALF_UP);
        producao.setCustoTotal(custoTotalProducao);
        producao.setCustoUnitario(custoUnitarioProduzido);

        ProducaoEntity salva = producaoRepository.save(producao);

        // ── Credita o PF/MPPF produzido pelo custo real do que foi consumido ──
        // É aqui que a produção deixa de ser só movimento de quantidade e passa a
        // transportar valor: o custo dos insumos vira o custo do produto acabado.
        custeioService.registrarEntrada(produto, empresaId, quantidade,
                custoUnitarioProduzido, OrigemEntradaEstoque.PRODUCAO, salva.getCodProducao(),
                LocalDate.now(), "Produção #" + salva.getCodProducao() + " - " + produto.getNome());

        return salva;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ESTORNAR (reverter uma produção)
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public ProducaoEntity estornar(Long codProducao, Long empresaId) {
        ProducaoEntity producao = buscarPorId(codProducao, empresaId);

        if (Boolean.TRUE.equals(producao.getEstornada()))
            throw new IllegalArgumentException("Esta produção já foi estornada anteriormente.");

        // Devolve insumos aos estoques de MP — ENTRADA pelo custo com que saíram.
        // Usar o custo de hoje aqui contaminaria o custo médio do insumo com um
        // valor que nunca foi pago; por isso o snapshot em producao_item.
        for (ProducaoItemEntity item : producao.getItensConsumidos()) {
            ProdutoEntity insumo = item.getInsumo();
            BigDecimal devolvida = nvl(item.getQuantidadeConsumida());
            BigDecimal custoSnapshot = item.getCustoUnitario() != null
                    ? item.getCustoUnitario()
                    : (insumo.getCustoMedio() != null ? insumo.getCustoMedio() : BigDecimal.ZERO);
            custeioService.registrarEntrada(insumo, empresaId, devolvida, custoSnapshot,
                    OrigemEntradaEstoque.ESTORNO_PRODUCAO, producao.getCodProducao(),
                    LocalDate.now(), "Estorno da produção #" + producao.getCodProducao());
        }

        // Retira do estoque do produto produzido — SAÍDA, não altera custo médio
        ProdutoEntity produto = producao.getProduto();
        BigDecimal novoEstoque = nvl(produto.getQuantidadeEstoque())
                .subtract(nvl(producao.getQuantidadeProduzida()));
        produto.setQuantidadeEstoque(novoEstoque.max(BigDecimal.ZERO)); // não vai negativo
        produtoRepository.save(produto);

        producao.setEstornada(true);
        return producaoRepository.save(producao);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESOLUÇÃO RECURSIVA DE INSUMOS (MPs folha)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Percorre a composição do produto acumulando somente MPs folha.
     * MPPFs intermediários são expandidos — seus MPs são incluídos com
     * a quantidade multiplicada pelo fator do nível superior.
     * O Set `visitados` evita loops em composições circulares.
     *
     * @param produto   produto a ser expandido
     * @param fator     multiplicador acumulado do nível atual
     * @param acumulado mapa de acúmulo: codMP → quantidade total necessária
     * @param visitados set de IDs já visitados neste ramo (clonado por ramo)
     */
    private void resolverInsumos(ProdutoEntity produto,
                                  BigDecimal fator,
                                  Map<Long, BigDecimal> acumulado,
                                  Set<Long> visitados) {
        if (!visitados.add(produto.getCodProduto())) return; // ciclo detectado

        List<ProdutoMateriaPrimaEntity> composicao =
                materiaPrimaRepository.findByIdCodProdutoFinal(produto.getCodProduto());

        for (ProdutoMateriaPrimaEntity entrada : composicao) {
            ProdutoEntity insumo = entrada.getMateriaPrima();
            BigDecimal qtd = entrada.getQuantidade().multiply(fator);
            TipoProduto tipoInsumo = insumo.getTipo() != null ? insumo.getTipo() : TipoProduto.MP;

            if (tipoInsumo == TipoProduto.MP || tipoInsumo == TipoProduto.MPPF) {
                // Acumula o insumo direto — MPPFs intermediários não são expandidos
                acumulado.merge(insumo.getCodProduto(), qtd, BigDecimal::add);
            }
            // PF dentro de composição: ignorado (não faz sentido de negócio)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDAÇÕES INTERNAS
    // ═══════════════════════════════════════════════════════════════════════

    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    /** Quantidade legível: "0,35" em vez de "0,3500". */
    private static String fmtQtd(BigDecimal v) {
        return nvl(v).stripTrailingZeros().toPlainString();
    }

    private ProdutoEntity buscarProdutoValidado(Long codProduto, Long empresaId) {
        ProdutoEntity produto = produtoRepository.findByIdAndEmpresaId(codProduto, empresaId)
                .orElseThrow(() -> new RuntimeException(
                        "Produto não encontrado ou não pertence a esta empresa."));

        TipoProduto tipo = produto.getTipo();
        if (tipo != TipoProduto.PF && tipo != TipoProduto.MPPF) {
            throw new IllegalArgumentException(
                    "Apenas produtos do tipo PF (Produto Final) ou MPPF (Matéria-Prima/Produto Final) " +
                    "podem ser produzidos. O produto '" + produto.getNome() + "' é do tipo " + tipo + ".");
        }
        return produto;
    }
}
