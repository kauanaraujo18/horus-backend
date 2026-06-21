package com.horus.projeto.services;

import com.horus.projeto.dto.ItemVendaDTO;
import com.horus.projeto.dto.ItemVendaResponseDTO;
import com.horus.projeto.dto.VendaRequestDTO;
import com.horus.projeto.dto.VendaResponseDTO;
import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.ProdutoVendaEntity;
import com.horus.projeto.entities.VendaEntity;
import com.horus.projeto.enums.OrigemLancamento;
import com.horus.projeto.enums.TipoProduto;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.ProdutoRepository;
import com.horus.projeto.repositories.VendaRepository;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * VendaService — responsável exclusivamente pelo registro de vendas no caixa.
 *
 * Regras de estoque (pós-reestruturação com módulo Produção):
 *  - MP  : não pode ser vendida diretamente no caixa.
 *  - PF  : vende do próprio estoque (abastecido via módulo Produção).
 *  - MPPF: vende do próprio estoque (abastecido via módulo Produção).
 *  - R   : vende do próprio estoque (comprado via Contas a Pagar ou ajuste manual).
 *
 * A resolução recursiva de insumos foi movida para ProducaoService,
 * que é o local correto do ciclo de vida de produção.
 */
@Service
public class VendaService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private LancamentoFinanceiroService lancamentoService;
    @Autowired private ParametrosFinanceiroService parametrosService;

    @Transactional
    public VendaEntity registrarVenda(VendaRequestDTO dadosVenda, Long empresaId) {

        VendaEntity venda = new VendaEntity();
        // data_venda = data de pagamento informada no PDV (ou hoje), preservando a hora atual.
        // data_registro = sempre o timestamp real do registro.
        java.time.LocalDate dataPagamento = dadosVenda.getDataVenda() != null
                ? dadosVenda.getDataVenda() : java.time.LocalDate.now();
        venda.setDataVenda(dataPagamento.atTime(java.time.LocalTime.now()));
        venda.setDataRegistro(LocalDateTime.now());
        venda.setQtdParcelas(dadosVenda.getQtdParcelas() != null ? dadosVenda.getQtdParcelas() : 1);

        BigDecimal desconto  = nvl(dadosVenda.getDesconto());
        BigDecimal acrescimo = nvl(dadosVenda.getAcrescimo());
        venda.setDesconto(desconto);
        venda.setAcrescimo(acrescimo);
        venda.setValorDinheiro(nvl(dadosVenda.getValorDinheiro()));
        venda.setValorPix(nvl(dadosVenda.getValorPix()));
        venda.setValorCredito(nvl(dadosVenda.getValorCredito()));
        venda.setValorDebito(nvl(dadosVenda.getValorDebito()));

        List<ProdutoVendaEntity> itensParaSalvar = new ArrayList<>();
        BigDecimal somaTotalItens = BigDecimal.ZERO;

        if (dadosVenda.getItens() != null) {
            for (ItemVendaDTO itemDTO : dadosVenda.getItens()) {

                ProdutoEntity produto = produtoRepository
                        .findByIdAndEmpresaId(itemDTO.getCodProduto(), empresaId)
                        .orElseThrow(() -> new RuntimeException(
                                "Produto ID " + itemDTO.getCodProduto() +
                                " não encontrado ou não pertence a esta empresa."));

                TipoProduto tipo = produto.getTipo() != null ? produto.getTipo() : TipoProduto.R;

                // Matéria-Prima não é comercializada no caixa — produção é papel do módulo Produção
                if (tipo == TipoProduto.MP) {
                    throw new RuntimeException(
                            "O produto '" + produto.getNome() +
                            "' é uma Matéria-Prima e não pode ser vendido no caixa. " +
                            "Use o módulo Produção para transformá-lo em produto final.");
                }

                // PF, MPPF e R: todos debitam do próprio estoque
                int estoqueAtual   = produto.getQuantidadeEstoque() != null ? produto.getQuantidadeEstoque() : 0;
                int qtdSolicitada  = itemDTO.getQuantidade();

                if (estoqueAtual < qtdSolicitada) {
                    throw new RuntimeException(String.format(
                            "Estoque insuficiente para '%s'. Disponível: %d | Solicitado: %d",
                            produto.getNome(), estoqueAtual, qtdSolicitada));
                }

                produto.setQuantidadeEstoque(estoqueAtual - qtdSolicitada);
                produtoRepository.save(produto);

                // Monta item da venda
                ProdutoVendaEntity item = new ProdutoVendaEntity();
                item.setProduto(produto);
                item.setQuantidade(qtdSolicitada);

                BigDecimal precoUnitario = produto.getValor() != null ? produto.getValor() : BigDecimal.ZERO;
                item.setValorUnitario(precoUnitario);
                BigDecimal subTotal = precoUnitario.multiply(new BigDecimal(qtdSolicitada));
                item.setValorTotalItem(subTotal);
                item.setVenda(venda);

                itensParaSalvar.add(item);
                somaTotalItens = somaTotalItens.add(subTotal);
            }
        }

        venda.setItens(itensParaSalvar);

        BigDecimal valorFinal = somaTotalItens.add(acrescimo).subtract(desconto);
        if (valorFinal.compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("O valor total da venda não pode ser negativo.");
        venda.setValorTotal(valorFinal);

        BigDecimal totalPago = nvl(dadosVenda.getValorDinheiro())
                .add(nvl(dadosVenda.getValorPix()))
                .add(nvl(dadosVenda.getValorCredito()))
                .add(nvl(dadosVenda.getValorDebito()));
        venda.setValorPago(totalPago);

        BigDecimal troco = totalPago.subtract(valorFinal);
        venda.setTroco(troco.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : troco);

        venda.setEmpresa(empresaRepository.getReferenceById(empresaId));
        venda.getItens().forEach(item -> item.setVenda(venda));

        VendaEntity salva = vendaRepository.save(venda);

        // ── Automação financeira: ENTRADA por classe × conta (rateio) ──────────────
        // Dinheiro (líquido de troco) → Caixa padrão; Pix/Débito/Crédito → Banco padrão.
        // Soma por classe = receita por categoria; soma por conta = dinheiro por conta.
        // Best-effort: uma falha de classificação NUNCA derruba a venda.
        gerarLancamentosDaVenda(salva, empresaId);

        return salva;
    }

    private void gerarLancamentosDaVenda(VendaEntity venda, Long empresaId) {
        // Itens com classe de receita (os únicos que podem ser lançados)
        List<ProdutoVendaEntity> itens = new ArrayList<>();
        for (ProdutoVendaEntity item : venda.getItens()) {
            if (item.getProduto() != null && item.getProduto().getCodClassePadrao() != null) itens.add(item);
        }
        BigDecimal soma = itens.stream().map(i -> nvl(i.getValorTotalItem())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itens.isEmpty() || soma.signum() <= 0) return;

        // Buckets de conta por forma de pagamento (caixa líquido de troco)
        Long caixaPadrao = parametrosService.getCaixaPadrao(empresaId);
        Long bancoPadrao = parametrosService.getBancoPadrao(empresaId);
        BigDecimal valorCaixa = nvl(venda.getValorDinheiro()).subtract(nvl(venda.getTroco())).max(BigDecimal.ZERO);
        BigDecimal valorBanco = nvl(venda.getValorPix()).add(nvl(venda.getValorCredito())).add(nvl(venda.getValorDebito()));

        java.time.LocalDate dataMov = venda.getDataVenda().toLocalDate();
        postarRateado(venda, empresaId, itens, soma, caixaPadrao, valorCaixa, dataMov);
        postarRateado(venda, empresaId, itens, soma, bancoPadrao, valorBanco, dataMov);
    }

    /**
     * Distribui o valor de um bucket (uma conta) entre os itens, proporcional ao valor de cada item.
     * Usa arredondamento cumulativo: a soma dos lançamentos fica EXATA = valorBucket (sem sobra de centavos).
     */
    private void postarRateado(VendaEntity venda, Long empresaId, List<ProdutoVendaEntity> itens,
                               BigDecimal soma, Long contaId, BigDecimal valorBucket, java.time.LocalDate dataMov) {
        if (valorBucket.signum() <= 0) return;
        BigDecimal acumPeso = BigDecimal.ZERO;
        BigDecimal acumValor = BigDecimal.ZERO;
        for (ProdutoVendaEntity item : itens) {
            acumPeso = acumPeso.add(nvl(item.getValorTotalItem()));
            BigDecimal alvo = valorBucket.multiply(acumPeso).divide(soma, 2, java.math.RoundingMode.HALF_UP);
            BigDecimal share = alvo.subtract(acumValor);
            acumValor = alvo;
            if (share.signum() > 0) {
                lancamentoService.postarSeValido(empresaId, item.getProduto().getCodClassePadrao(), share, dataMov,
                        "Venda #" + venda.getCodVenda() + " - " + item.getProduto().getNome(),
                        OrigemLancamento.VENDA, venda.getCodVenda(), contaId);
            }
        }
    }

    // ── Estorno (cancelamento) ───────────────────────────────────────────────

    /**
     * Estorna uma venda: devolve o estoque dos itens, estorna os lançamentos
     * financeiros gerados por ela e marca a venda como estornada (mantém histórico).
     */
    @Transactional
    public VendaEntity estornarVenda(Long codVenda, Long empresaId) {
        VendaEntity venda = vendaRepository.findByCodVendaAndEmpresaId(codVenda, empresaId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada ou não pertence a esta empresa."));
        if (Boolean.TRUE.equals(venda.getEstornada()))
            throw new RuntimeException("Esta venda já foi estornada.");

        // 1) Devolve o estoque de cada item
        for (ProdutoVendaEntity item : venda.getItens()) {
            ProdutoEntity produto = item.getProduto();
            if (produto == null) continue;
            int atual = produto.getQuantidadeEstoque() != null ? produto.getQuantidadeEstoque() : 0;
            int qtd = item.getQuantidade() != null ? item.getQuantidade() : 0;
            produto.setQuantidadeEstoque(atual + qtd);
            produtoRepository.save(produto);
        }

        // 2) Estorna os lançamentos financeiros (entradas) desta venda
        lancamentoService.estornarPorOrigem(OrigemLancamento.VENDA, codVenda);

        // 3) Marca como estornada
        venda.setEstornada(true);
        return vendaRepository.save(venda);
    }

    /**
     * Gera os lançamentos financeiros das vendas que ainda não os têm (vendas antigas /
     * anteriores ao módulo financeiro), datados na data_venda. Idempotente e não estornadas.
     * Retorna quantas vendas foram reprocessadas.
     */
    @Transactional
    public int reprocessarLancamentos(Long empresaId) {
        List<VendaEntity> vendas = vendaRepository.findByEmpresaId(empresaId, Sort.by(Sort.Direction.ASC, "dataVenda"));
        int reprocessadas = 0;
        for (VendaEntity venda : vendas) {
            if (Boolean.TRUE.equals(venda.getEstornada())) continue;
            if (lancamentoService.temLancamentoAtivo(OrigemLancamento.VENDA, venda.getCodVenda())) continue;
            gerarLancamentosDaVenda(venda, empresaId);
            reprocessadas++;
        }
        return reprocessadas;
    }

    // ── Relatórios ──────────────────────────────────────────────────────────

    public List<VendaResponseDTO> listarPorEmpresa(Long empresaId) {
        return vendaRepository
                .findByEmpresaId(empresaId, Sort.by(Sort.Direction.DESC, "dataVenda"))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    private VendaResponseDTO toDTO(VendaEntity venda) {
        VendaResponseDTO dto = new VendaResponseDTO();
        dto.setCodVenda(venda.getCodVenda());
        dto.setDataVenda(venda.getDataVenda());
        dto.setValorTotal(venda.getValorTotal());
        dto.setDesconto(venda.getDesconto());
        dto.setAcrescimo(venda.getAcrescimo());
        dto.setValorDinheiro(venda.getValorDinheiro());
        dto.setValorPix(venda.getValorPix());
        dto.setValorCredito(venda.getValorCredito());
        dto.setValorDebito(venda.getValorDebito());
        dto.setValorPago(venda.getValorPago());
        dto.setTroco(venda.getTroco());
        dto.setEstornada(Boolean.TRUE.equals(venda.getEstornada()));
        if (venda.getItens() != null) {
            dto.setItens(venda.getItens().stream().map(item -> {
                ItemVendaResponseDTO i = new ItemVendaResponseDTO();
                i.setNome(item.getProduto() != null ? item.getProduto().getNome() : "Produto Removido");
                i.setCodProduto(item.getProduto() != null ? item.getProduto().getCodProduto() : null);
                i.setQuantidade(item.getQuantidade());
                i.setValorUnitario(item.getValorUnitario());
                i.setValorTotalItem(item.getValorTotalItem());
                return i;
            }).collect(Collectors.toList()));
        }
        return dto;
    }

    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
