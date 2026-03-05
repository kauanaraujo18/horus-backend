package com.horus.projeto.services;

import com.horus.projeto.dto.ItemVendaDTO;
import com.horus.projeto.dto.ItemVendaResponseDTO;
import com.horus.projeto.dto.VendaRequestDTO;
import com.horus.projeto.dto.VendaResponseDTO;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.ProdutoVendaEntity;
import com.horus.projeto.entities.VendaEntity;
import com.horus.projeto.repositories.ProdutoRepository;
import com.horus.projeto.repositories.VendaRepository;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;

@Service
public class VendaService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional
    public VendaEntity registrarVenda(VendaRequestDTO dadosVenda) {
        
        // ==========================================
        // 1. PREPARAR O CABEÇALHO DA VENDA
        // ==========================================
        VendaEntity venda = new VendaEntity();
        
        // Datas
        venda.setDataVenda(LocalDateTime.now());
        venda.setDataRegistro(LocalDateTime.now());
        
        // Parcelas (Padrão 1 se vier nulo)
        venda.setQtdParcelas(dadosVenda.getQtdParcelas() != null ? dadosVenda.getQtdParcelas() : 1);

        // Descontos e Acréscimos (Padrão ZERO se vier nulo)
        BigDecimal desconto = dadosVenda.getDesconto() != null ? dadosVenda.getDesconto() : BigDecimal.ZERO;
        BigDecimal acrescimo = dadosVenda.getAcrescimo() != null ? dadosVenda.getAcrescimo() : BigDecimal.ZERO;
        
        venda.setDesconto(desconto);
        venda.setAcrescimo(acrescimo);

        // ==========================================
        // 2. CAPTURAR FORMAS DE PAGAMENTO
        // ==========================================
        // Pega os valores individuais do DTO. Se vier null, considera 0.00
        BigDecimal vDinheiro = dadosVenda.getValorDinheiro() != null ? dadosVenda.getValorDinheiro() : BigDecimal.ZERO;
        BigDecimal vPix = dadosVenda.getValorPix() != null ? dadosVenda.getValorPix() : BigDecimal.ZERO;
        BigDecimal vCredito = dadosVenda.getValorCredito() != null ? dadosVenda.getValorCredito() : BigDecimal.ZERO;
        BigDecimal vDebito = dadosVenda.getValorDebito() != null ? dadosVenda.getValorDebito() : BigDecimal.ZERO;

        // Salva discriminado no banco
        venda.setValorDinheiro(vDinheiro);
        venda.setValorPix(vPix);
        venda.setValorCredito(vCredito);
        venda.setValorDebito(vDebito);

        // ==========================================
        // 3. PROCESSAR ITENS DA VENDA
        // ==========================================
        List<ProdutoVendaEntity> itensParaSalvar = new ArrayList<>();
        BigDecimal somaTotalItens = BigDecimal.ZERO;

        if (dadosVenda.getItens() != null) {
            for (ItemVendaDTO itemDTO : dadosVenda.getItens()) {
                
                // Busca Produto no Banco (Erro se não achar)
                ProdutoEntity produto = produtoRepository.findById(itemDTO.getCodProduto())
                        .orElseThrow(() -> new RuntimeException("Produto não encontrado. ID: " + itemDTO.getCodProduto()));

                // Cria entidade do Item
                ProdutoVendaEntity item = new ProdutoVendaEntity();
                item.setProduto(produto);
                item.setQuantidade(itemDTO.getQuantidade());

                // Define Preço Unitário (Usa o enviado pelo front ou o do cadastro se for nulo)
                BigDecimal precoUnitario = itemDTO.getValorUnitario() != null 
                        ? itemDTO.getValorUnitario() 
                        : produto.getValor();

                item.setValorUnitario(precoUnitario);

                // Calcula Subtotal do Item (Qtd * Valor Unit)
                BigDecimal subTotal = precoUnitario.multiply(new BigDecimal(itemDTO.getQuantidade()));
                item.setValorTotalItem(subTotal);

                // Vincula à Venda Pai
                item.setVenda(venda);
                
                // Adiciona na lista e soma no totalizador
                itensParaSalvar.add(item);
                somaTotalItens = somaTotalItens.add(subTotal);
            }
        }

        // Vincula a lista de itens à venda
        venda.setItens(itensParaSalvar);

        // ==========================================
        // 4. CÁLCULOS FINAIS (TOTAL, PAGO E TROCO)
        // ==========================================
        
        // Valor Final da Venda = Soma Itens + Acréscimo - Desconto
        BigDecimal valorFinalVenda = somaTotalItens.add(acrescimo).subtract(desconto);
        
        // Validação de Segurança
        if (valorFinalVenda.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("O valor total da venda não pode ser negativo.");
        }
        venda.setValorTotal(valorFinalVenda);

        // Valor Total Pago = Soma dos 4 métodos
        BigDecimal valorTotalPagoCliente = vDinheiro.add(vPix).add(vCredito).add(vDebito);
        venda.setValorPago(valorTotalPagoCliente);

        // Troco = Valor Pago - Valor Final
        BigDecimal trocoCalculado = valorTotalPagoCliente.subtract(valorFinalVenda);

        // Se o troco for negativo (fiado/erro), salvamos como ZERO no banco
        if (trocoCalculado.compareTo(BigDecimal.ZERO) < 0) {
            trocoCalculado = BigDecimal.ZERO;
        }
        venda.setTroco(trocoCalculado);

        // ==========================================
        // 5. SALVAR NO BANCO (CASCATA SALVA ITENS)
        // ==========================================
        return vendaRepository.save(venda);
    }

    public List<VendaResponseDTO> listarTodasVendas() {
        // Busca todas as vendas ordenadas pela Data (Mais recentes primeiro)
        List<VendaEntity> vendas = vendaRepository.findAll(Sort.by(Sort.Direction.DESC, "dataVenda"));

        // Converte de Entity para DTO
        return vendas.stream().map(venda -> {
            VendaResponseDTO dto = new VendaResponseDTO();
            
            dto.setCodVenda(venda.getCodVenda());
            dto.setDataVenda(venda.getDataVenda());
            dto.setValorTotal(venda.getValorTotal());
            dto.setDesconto(venda.getDesconto());
            dto.setAcrescimo(venda.getAcrescimo());
            
            // Pagamentos
            dto.setValorDinheiro(venda.getValorDinheiro());
            dto.setValorPix(venda.getValorPix());
            dto.setValorCredito(venda.getValorCredito());
            dto.setValorDebito(venda.getValorDebito());
            dto.setValorPago(venda.getValorPago());
            dto.setTroco(venda.getTroco());

            // Converte os Itens
            if (venda.getItens() != null) {
                List<ItemVendaResponseDTO> itensDTO = venda.getItens().stream().map(item -> {
                    ItemVendaResponseDTO itemDto = new ItemVendaResponseDTO();
                    // Aqui pegamos o nome do produto através do relacionamento
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