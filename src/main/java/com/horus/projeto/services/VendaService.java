package com.horus.projeto.services;

import com.horus.projeto.dto.ItemVendaDTO;
import com.horus.projeto.dto.ItemVendaResponseDTO;
import com.horus.projeto.dto.VendaRequestDTO;
import com.horus.projeto.dto.VendaResponseDTO;
import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.ProdutoVendaEntity;
import com.horus.projeto.entities.VendaEntity;
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

@Service
public class VendaService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Transactional
    public VendaEntity registrarVenda(VendaRequestDTO dadosVenda, Long empresaId) {
        
        VendaEntity venda = new VendaEntity();
        
        venda.setDataVenda(LocalDateTime.now());
        venda.setDataRegistro(LocalDateTime.now());
        venda.setQtdParcelas(dadosVenda.getQtdParcelas() != null ? dadosVenda.getQtdParcelas() : 1);

        BigDecimal desconto = dadosVenda.getDesconto() != null ? dadosVenda.getDesconto() : BigDecimal.ZERO;
        BigDecimal acrescimo = dadosVenda.getAcrescimo() != null ? dadosVenda.getAcrescimo() : BigDecimal.ZERO;
        venda.setDesconto(desconto);
        venda.setAcrescimo(acrescimo);

        BigDecimal vDinheiro = dadosVenda.getValorDinheiro() != null ? dadosVenda.getValorDinheiro() : BigDecimal.ZERO;
        BigDecimal vPix = dadosVenda.getValorPix() != null ? dadosVenda.getValorPix() : BigDecimal.ZERO;
        BigDecimal vCredito = dadosVenda.getValorCredito() != null ? dadosVenda.getValorCredito() : BigDecimal.ZERO;
        BigDecimal vDebito = dadosVenda.getValorDebito() != null ? dadosVenda.getValorDebito() : BigDecimal.ZERO;

        venda.setValorDinheiro(vDinheiro);
        venda.setValorPix(vPix);
        venda.setValorCredito(vCredito);
        venda.setValorDebito(vDebito);

        List<ProdutoVendaEntity> itensParaSalvar = new ArrayList<>();
        BigDecimal somaTotalItens = BigDecimal.ZERO;

        if (dadosVenda.getItens() != null) {
            for (ItemVendaDTO itemDTO : dadosVenda.getItens()) {
                
                ProdutoEntity produto = produtoRepository.findById(itemDTO.getCodProduto())
                        .orElseThrow(() -> new RuntimeException("Produto não encontrado. ID: " + itemDTO.getCodProduto()));

                // =======================================================
                // REGRA DE NEGÓCIO: VALIDAÇÃO DE ESTOQUE (FAIL-FAST)
                // =======================================================
                if (produto.getQuantidadeEstoque() < itemDTO.getQuantidade()) {
                    throw new RuntimeException(
                        String.format("Estoque insuficiente para o produto '%s'. Disponível: %d | Solicitado: %d", 
                        produto.getNome(), produto.getQuantidadeEstoque(), itemDTO.getQuantidade())
                    );
                }
                // IMPORTANTE: NÃO fazemos produto.setQuantidadeEstoque() aqui. 
                // Nossa Trigger no banco fará a subtração de forma atômica durante o commit do Hibernate!

                ProdutoVendaEntity item = new ProdutoVendaEntity();
                item.setProduto(produto);
                item.setQuantidade(itemDTO.getQuantidade());

                BigDecimal precoUnitario = itemDTO.getValorUnitario() != null 
                        ? itemDTO.getValorUnitario() 
                        : produto.getValor();

                item.setValorUnitario(precoUnitario);
                BigDecimal subTotal = precoUnitario.multiply(new BigDecimal(itemDTO.getQuantidade()));
                item.setValorTotalItem(subTotal);
                item.setVenda(venda);
                
                itensParaSalvar.add(item);
                somaTotalItens = somaTotalItens.add(subTotal);
            }
        }

        venda.setItens(itensParaSalvar);
        
        BigDecimal valorFinalVenda = somaTotalItens.add(acrescimo).subtract(desconto);
        if (valorFinalVenda.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("O valor total da venda não pode ser negativo.");
        }
        venda.setValorTotal(valorFinalVenda);

        BigDecimal valorTotalPagoCliente = vDinheiro.add(vPix).add(vCredito).add(vDebito);
        venda.setValorPago(valorTotalPagoCliente);

        BigDecimal trocoCalculado = valorTotalPagoCliente.subtract(valorFinalVenda);
        if (trocoCalculado.compareTo(BigDecimal.ZERO) < 0) {
            trocoCalculado = BigDecimal.ZERO;
        }
        venda.setTroco(trocoCalculado);

        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);
        venda.setEmpresa(empresa);

        if (venda.getItens() != null) {
            venda.getItens().forEach(item -> item.setVenda(venda));
        }

        // ==========================================
        // AO SALVAR: O Hibernate gera o INSERT de venda e de produto_venda. 
        // Nesse momento, a Trigger do banco (trg_deduzir_estoque) é acionada nativamente!
        // ==========================================
        return vendaRepository.save(venda);
    }

    public List<VendaResponseDTO> listarPorEmpresa(Long empresaId) {
        List<VendaEntity> vendas = vendaRepository.findByEmpresaId(
            empresaId, 
            Sort.by(Sort.Direction.DESC, "dataVenda")
        );

        return vendas.stream().map(venda -> {
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
                List<ItemVendaResponseDTO> itensDTO = venda.getItens().stream().map(item -> {
                    ItemVendaResponseDTO itemDto = new ItemVendaResponseDTO();
                    itemDto.setNome(item.getProduto() != null ? item.getProduto().getNome() : "Produto Removido");
                    itemDto.setCodProduto(item.getProduto() != null ? item.getProduto().getCodProduto() : null);
                    itemDto.setQuantidade(item.getQuantidade());
                    itemDto.setValorUnitario(item.getValorUnitario());
                    itemDto.setValorTotalItem(item.getValorTotalItem());
                    return itemDto;
                }).collect(Collectors.toList());
                dto.setItens(itensDTO);
            }
            return dto;
        }).collect(Collectors.toList());
    }
}