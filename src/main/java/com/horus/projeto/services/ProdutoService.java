package com.horus.projeto.services;

import com.horus.projeto.dto.MateriaPrimaItemDTO;
import com.horus.projeto.dto.ProdutoEsquemaNodeDTO;
import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.MateriaPrimaId;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.ProdutoMateriaPrimaEntity;
import com.horus.projeto.enums.TipoProduto;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.ProdutoMateriaPrimaRepository;
import com.horus.projeto.repositories.ProdutoRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repository;
    private final EmpresaRepository empresaRepository;
    private final ProdutoMateriaPrimaRepository materiaPrimaRepository;
    private final com.horus.projeto.repositories.ClasseFinanceiraRepository classeFinanceiraRepository;

    public List<ProdutoEntity> listarPorEmpresa(Long empresaId) {
        return repository.findByEmpresaId(empresaId);
    }

    public ProdutoEntity buscarPorId(Long id, Long empresaId) {
        return repository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RuntimeException("Acesso Negado: Produto não encontrado ou não pertence a esta empresa."));
    }

    @Transactional
    public ProdutoEntity salvar(ProdutoEntity produto, Long empresaId) {
        if (produto.getCodProduto() != null) {
            return atualizar(produto.getCodProduto(), produto, empresaId);
        }

        if (produto.getCodigo() != null && !produto.getCodigo().isBlank()
                && repository.existsByCodigoAndEmpresaId(produto.getCodigo(), empresaId)) {
            throw new IllegalArgumentException("Erro: Já existe um Produto cadastrado com este Código na sua loja.");
        }

        validarClasseProduto(empresaId, produto.getCodClassePadrao());

        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);
        produto.setEmpresa(empresa);

        return repository.save(produto);
    }

    /** Produto só pode usar classe financeira ANALÍTICA de RECEITA. */
    private void validarClasseProduto(Long empresaId, Long codClassePadrao) {
        if (codClassePadrao == null) return;
        com.horus.projeto.entities.ClasseFinanceiraEntity classe =
                classeFinanceiraRepository.findByCodClasseAndEmpresaId(codClassePadrao, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Classe financeira não encontrada nesta empresa."));
        if (classe.getNivel() != com.horus.projeto.enums.NivelClasse.ANALITICA)
            throw new IllegalArgumentException("O produto deve usar uma classe ANALÍTICA.");
        if (classe.getTipo() != com.horus.projeto.enums.TipoClasse.RECEITA)
            throw new IllegalArgumentException("O produto deve usar uma classe de RECEITA.");
    }

    @Transactional
    public ProdutoEntity atualizar(Long id, ProdutoEntity produtoAtualizada, Long empresaId) {
        ProdutoEntity produtoExistente = buscarPorId(id, empresaId);

        boolean codigoMudou = produtoAtualizada.getCodigo() != null
                && !produtoAtualizada.getCodigo().isBlank()
                && !produtoAtualizada.getCodigo().equals(produtoExistente.getCodigo());

        if (codigoMudou && repository.existsByCodigoAndEmpresaId(produtoAtualizada.getCodigo(), empresaId)) {
            throw new IllegalArgumentException("Erro: Já existe um Produto cadastrado com este Código.");
        }

        produtoExistente.setCodigo(produtoAtualizada.getCodigo());
        produtoExistente.setNome(produtoAtualizada.getNome());
        produtoExistente.setValor(produtoAtualizada.getValor());

        if (produtoAtualizada.getValorCusto() != null) {
            produtoExistente.setValorCusto(produtoAtualizada.getValorCusto());
        }
        if (produtoAtualizada.getTipo() != null) {
            produtoExistente.setTipo(produtoAtualizada.getTipo());
        }
        if (produtoAtualizada.getQuantidadeEstoque() != null) {
            produtoExistente.setQuantidadeEstoque(produtoAtualizada.getQuantidadeEstoque());
        }
        if (produtoAtualizada.getUnidadeMedida() != null) {
            produtoExistente.setUnidadeMedida(produtoAtualizada.getUnidadeMedida());
        }
        if (produtoAtualizada.getReferencia() != null) {
            produtoExistente.setReferencia(produtoAtualizada.getReferencia());
        }

        // Classe financeira padrão: setada sempre (permite limpar), validada quando presente
        validarClasseProduto(empresaId, produtoAtualizada.getCodClassePadrao());
        produtoExistente.setCodClassePadrao(produtoAtualizada.getCodClassePadrao());

        return repository.save(produtoExistente);
    }

    @Transactional
    public void deletar(Long id, Long empresaId) {
        ProdutoEntity produto = buscarPorId(id, empresaId);
        repository.delete(produto);
    }

    // =========================================================
    // MÓDULO DE PRODUÇÃO - Matérias-Primas
    // =========================================================

    public List<ProdutoMateriaPrimaEntity> listarMateriasPrimas(Long produtoId, Long empresaId) {
        ProdutoEntity produto = buscarPorId(produtoId, empresaId);
        return produto.getComposicao();
    }

    @Transactional
    public ProdutoEntity vincularMateriasPrimas(Long produtoId, List<MateriaPrimaItemDTO> itens, Long empresaId) {
        ProdutoEntity produto = buscarPorId(produtoId, empresaId);

        if (produto.getTipo() == TipoProduto.MP || produto.getTipo() == TipoProduto.R) {
            throw new IllegalArgumentException("Erro: Apenas produtos do tipo PF ou MPPF podem ter matérias-primas vinculadas.");
        }

        List<Long> ids = itens.stream().map(MateriaPrimaItemDTO::getId).toList();

        if (ids.contains(produtoId)) {
            throw new IllegalArgumentException("Erro: Um produto não pode ser matéria-prima de si mesmo.");
        }

        if (produto.getComposicao() == null) {
            produto.setComposicao(new java.util.ArrayList<>());
        }
        List<ProdutoMateriaPrimaEntity> composicao = produto.getComposicao();

        // Atualização in-place: clear() + re-add com a mesma chave composta faz o Hibernate
        // agendar DELETE e re-INSERT do mesmo id na mesma transação → ObjectDeletedException.
        Map<Long, BigDecimal> desejadas = new LinkedHashMap<>();
        for (MateriaPrimaItemDTO item : itens) {
            desejadas.put(item.getId(), item.getQuantidade());
        }

        // 1. Remove apenas os insumos que saíram da composição
        composicao.removeIf(e -> !desejadas.containsKey(e.getId().getCodProdutoMateriaPrima()));

        // 2. Atualiza a quantidade dos que permaneceram
        Set<Long> existentes = new HashSet<>();
        for (ProdutoMateriaPrimaEntity entrada : composicao) {
            Long mpId = entrada.getId().getCodProdutoMateriaPrima();
            entrada.setQuantidade(desejadas.get(mpId));
            existentes.add(mpId);
        }

        // 3. Adiciona somente os insumos realmente novos
        for (Map.Entry<Long, BigDecimal> item : desejadas.entrySet()) {
            if (existentes.contains(item.getKey())) continue;
            ProdutoEntity mp = buscarPorId(item.getKey(), empresaId);
            MateriaPrimaId mpId = new MateriaPrimaId(produtoId, item.getKey());
            composicao.add(new ProdutoMateriaPrimaEntity(mpId, produto, mp, item.getValue()));
        }

        return repository.save(produto);
    }

    @Transactional
    public ProdutoEntity desvincularMateriaPrima(Long produtoId, Long materiaPrimaId, Long empresaId) {
        ProdutoEntity produto = buscarPorId(produtoId, empresaId);
        produto.getComposicao().removeIf(entry ->
                entry.getId().getCodProdutoMateriaPrima().equals(materiaPrimaId));
        return repository.save(produto);
    }

    public List<ProdutoEntity> listarPorTipo(TipoProduto tipo, Long empresaId) {
        return repository.findByTipoAndEmpresaId(tipo, empresaId);
    }

    public List<ProdutoEntity> listarMateriaisPrimasDisponiveis(Long empresaId) {
        return repository.findByTiposAndEmpresaId(
                List.of(TipoProduto.MP, TipoProduto.MPPF), empresaId);
    }

    // ── Esquema hierárquico de composição ────────────────────────────────────

    public List<ProdutoEsquemaNodeDTO> buildEsquema(Long empresaId) {
        List<ProdutoEntity> todos = repository.findByEmpresaId(empresaId);
        List<ProdutoMateriaPrimaEntity> todasComps = materiaPrimaRepository.findAllByEmpresaId(empresaId);

        Map<Long, ProdutoEntity> prodMap = todos.stream()
                .collect(Collectors.toMap(ProdutoEntity::getCodProduto, p -> p));

        // codProdutoFinal → lista de entradas de composição
        Map<Long, List<ProdutoMateriaPrimaEntity>> compMap = todasComps.stream()
                .collect(Collectors.groupingBy(c -> c.getId().getCodProdutoFinal()));

        // Produtos usados como componente de outro produto
        Set<Long> usadosComoComponente = todasComps.stream()
                .map(c -> c.getId().getCodProdutoMateriaPrima())
                .collect(Collectors.toSet());

        // Raízes = produtos que NÃO aparecem como componente de ninguém
        return todos.stream()
                .filter(p -> !usadosComoComponente.contains(p.getCodProduto()))
                .map(p -> buildNode(p, null, prodMap, compMap, new HashSet<>()))
                .collect(Collectors.toList());
    }

    // ── Análise de lucro unitário (custo de produção × valor de venda) ──────

    public List<Map<String, Object>> analiseLucro(Long empresaId) {
        List<ProdutoEntity> todos = repository.findByEmpresaId(empresaId);
        List<ProdutoMateriaPrimaEntity> todasComps = materiaPrimaRepository.findAllByEmpresaId(empresaId);

        Map<Long, ProdutoEntity> prodMap = todos.stream()
                .collect(Collectors.toMap(ProdutoEntity::getCodProduto, p -> p));
        Map<Long, List<ProdutoMateriaPrimaEntity>> compMap = todasComps.stream()
                .collect(Collectors.groupingBy(c -> c.getId().getCodProdutoFinal()));

        Map<Long, BigDecimal> memo = new HashMap<>();

        // Apenas produtos vendáveis ao consumidor final (R e PF) entram no ranking
        return todos.stream()
                .filter(p -> p.getTipo() == TipoProduto.R || p.getTipo() == TipoProduto.PF)
                .map(p -> {
            BigDecimal custo = custoProducao(p.getCodProduto(), prodMap, compMap, memo, new HashSet<>());
            BigDecimal valor = p.getValor() != null ? p.getValor() : BigDecimal.ZERO;
            BigDecimal lucro = valor.subtract(custo);
            BigDecimal margem = valor.compareTo(BigDecimal.ZERO) > 0
                    ? lucro.multiply(BigDecimal.valueOf(100)).divide(valor, 1, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("codProduto", p.getCodProduto());
            linha.put("nome", p.getNome());
            linha.put("tipo", p.getTipo() != null ? p.getTipo().name() : null);
            linha.put("valorVenda", valor);
            linha.put("custoProducao", custo);
            linha.put("lucroUnitario", lucro);
            linha.put("margem", margem);
            linha.put("possuiComposicao", compMap.containsKey(p.getCodProduto()));
            return linha;
        })
        .sorted((a, b) -> ((BigDecimal) b.get("lucroUnitario")).compareTo((BigDecimal) a.get("lucroUnitario")))
        .collect(Collectors.toList());
    }

    /**
     * Custo unitário de produção: se o produto tem composição, soma recursivamente
     * (custo do insumo × quantidade); senão, usa o valor de custo cadastrado.
     */
    private BigDecimal custoProducao(Long codProduto,
                                     Map<Long, ProdutoEntity> prodMap,
                                     Map<Long, List<ProdutoMateriaPrimaEntity>> compMap,
                                     Map<Long, BigDecimal> memo,
                                     Set<Long> visitados) {
        if (memo.containsKey(codProduto)) return memo.get(codProduto);
        if (!visitados.add(codProduto)) return BigDecimal.ZERO; // ciclo

        ProdutoEntity produto = prodMap.get(codProduto);
        if (produto == null) return BigDecimal.ZERO;

        List<ProdutoMateriaPrimaEntity> comps = compMap.getOrDefault(codProduto, List.of());
        BigDecimal custo;
        if (comps.isEmpty()) {
            custo = produto.getValorCusto() != null ? produto.getValorCusto() : BigDecimal.ZERO;
        } else {
            custo = BigDecimal.ZERO;
            for (ProdutoMateriaPrimaEntity comp : comps) {
                BigDecimal qtd = comp.getQuantidade() != null ? comp.getQuantidade() : BigDecimal.ONE;
                BigDecimal custoInsumo = custoProducao(comp.getId().getCodProdutoMateriaPrima(),
                        prodMap, compMap, memo, new HashSet<>(visitados));
                custo = custo.add(custoInsumo.multiply(qtd));
            }
        }
        memo.put(codProduto, custo);
        return custo;
    }

    private ProdutoEsquemaNodeDTO buildNode(ProdutoEntity produto,
                                             BigDecimal quantidade,
                                             Map<Long, ProdutoEntity> prodMap,
                                             Map<Long, List<ProdutoMateriaPrimaEntity>> compMap,
                                             Set<Long> visitados) {
        if (!visitados.add(produto.getCodProduto())) {
            // Ciclo detectado — retorna como folha
            return new ProdutoEsquemaNodeDTO(produto.getCodProduto(),
                    produto.getNome() + " ↺", tipoNome(produto),
                    produto.getQuantidadeEstoque(), quantidade, List.of());
        }

        List<ProdutoMateriaPrimaEntity> comps =
                compMap.getOrDefault(produto.getCodProduto(), List.of());

        List<ProdutoEsquemaNodeDTO> children = comps.stream()
                .map(comp -> {
                    ProdutoEntity filho = prodMap.get(comp.getId().getCodProdutoMateriaPrima());
                    if (filho == null) return null;
                    return buildNode(filho, comp.getQuantidade(), prodMap, compMap, new HashSet<>(visitados));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new ProdutoEsquemaNodeDTO(produto.getCodProduto(), produto.getNome(),
                tipoNome(produto), produto.getQuantidadeEstoque(), quantidade, children);
    }

    // Nome do tipo à prova de nulo (há produtos cadastrados sem tipo definido).
    private String tipoNome(ProdutoEntity produto) {
        return produto.getTipo() != null ? produto.getTipo().name() : null;
    }
}
