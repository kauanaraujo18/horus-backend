package com.horus.projeto.services;

import com.horus.projeto.dto.ItemVendaDTO;
import com.horus.projeto.dto.ItemVendaResponseDTO;
import com.horus.projeto.dto.VendaRequestDTO;
import com.horus.projeto.dto.VendaResponseDTO;
import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.ProdutoVendaEntity;
import com.horus.projeto.entities.VendaEntity;
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

    @Transactional
    public VendaEntity registrarVenda(VendaRequestDTO dadosVenda, Long empresaId) {

        VendaEntity venda = new VendaEntity();
        venda.setDataVenda(LocalDateTime.now());
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

        return vendaRepository.save(venda);
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
