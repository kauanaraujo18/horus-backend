package com.horus.projeto.services;

import com.horus.projeto.dto.ContaPagarItemDTO;
import com.horus.projeto.dto.ContaPagarParcelaDTO;
import com.horus.projeto.dto.ContaPagarRequestDTO;
import com.horus.projeto.entities.*;
import com.horus.projeto.repositories.ContaPagarRepository;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContaPagarService {

    private final ContaPagarRepository contaPagarRepository;
    private final ProdutoRepository produtoRepository;
    private final EmpresaRepository empresaRepository;

    /* ------------------------------------------------------------------
       CONSULTA
       ------------------------------------------------------------------ */

    public List<ContaPagarEntity> listarPorEmpresa(Long empresaId) {
        return contaPagarRepository.findByEmpresaIdOrderByDataRegistroDesc(empresaId);
    }

    public ContaPagarEntity buscarPorId(Long id, Long empresaId) {
        return contaPagarRepository.findByCodContaPagarAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RuntimeException(
                        "Acesso Negado: Conta a Pagar não encontrada ou não pertence a esta empresa."));
    }

    public List<ContaPagarEntity> pesquisar(String termo, Long empresaId) {
        return contaPagarRepository.buscarPorTermoEEmpresa(termo, empresaId);
    }

    /* ------------------------------------------------------------------
       SALVAR (POST) — adiciona estoque dos produtos vinculados
       ------------------------------------------------------------------ */

    @Transactional
    public ContaPagarEntity salvar(ContaPagarRequestDTO dto, Long empresaId) {
        validarDTO(dto);

        ContaPagarEntity conta = new ContaPagarEntity();
        conta.setDescricao(dto.getDescricao().trim());
        conta.setFornecedor(dto.getFornecedor() != null ? dto.getFornecedor().trim() : null);
        conta.setNumeroNotaFiscal(dto.getNumeroNotaFiscal() != null ? dto.getNumeroNotaFiscal().trim() : null);
        conta.setPaga(dto.getPaga() != null && dto.getPaga());
        conta.setEmpresa(empresaRepository.getReferenceById(empresaId));

        // ── ITENS (Fase 2: atualiza estoque) ──────────────────────────────
        BigDecimal valorTotal = BigDecimal.ZERO;
        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            for (ContaPagarItemDTO itemDTO : dto.getItens()) {
                ProdutoEntity produto = produtoRepository.findByIdAndEmpresaId(itemDTO.getCodProduto(), empresaId)
                        .orElseThrow(() -> new RuntimeException(
                                "Produto ID " + itemDTO.getCodProduto() + " não encontrado."));

                if (itemDTO.getQuantidade() == null || itemDTO.getQuantidade() <= 0) {
                    throw new IllegalArgumentException(
                            "Quantidade inválida para o produto: " + produto.getNome());
                }

                BigDecimal valorUnit = itemDTO.getValorUnitario() != null
                        ? itemDTO.getValorUnitario() : BigDecimal.ZERO;
                BigDecimal totalItem = valorUnit.multiply(new BigDecimal(itemDTO.getQuantidade()));

                ContaPagarItemEntity item = new ContaPagarItemEntity();
                item.setContaPagar(conta);
                item.setProduto(produto);
                item.setQuantidade(itemDTO.getQuantidade());
                item.setValorUnitario(valorUnit);
                item.setValorTotalItem(totalItem);
                conta.getItens().add(item);

                valorTotal = valorTotal.add(totalItem);

                // Fase 2: entrada em estoque
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + itemDTO.getQuantidade());
                produtoRepository.save(produto);
            }
        }
        conta.setValorTotal(valorTotal);

        // ── PARCELAS ──────────────────────────────────────────────────────
        if (dto.getParcelas() != null && !dto.getParcelas().isEmpty()) {
            for (ContaPagarParcelaDTO parcelaDTO : dto.getParcelas()) {
                ContaPagarParcelaEntity parcela = new ContaPagarParcelaEntity();
                parcela.setContaPagar(conta);
                parcela.setNumeroParcela(parcelaDTO.getNumeroParcela());
                parcela.setValorParcela(parcelaDTO.getValorParcela() != null
                        ? parcelaDTO.getValorParcela() : BigDecimal.ZERO);
                parcela.setDataVencimento(parcelaDTO.getDataVencimento());
                // Se a conta já está paga, todas as parcelas também ficam pagas
                boolean pagaParcela = parcelaDTO.getPaga() != null
                        ? parcelaDTO.getPaga() : conta.getPaga();
                parcela.setPaga(pagaParcela);
                conta.getParcelas().add(parcela);
            }
            // Data de vencimento principal = maior vencimento entre as parcelas
            conta.setDataVencimento(
                    conta.getParcelas().stream()
                            .map(ContaPagarParcelaEntity::getDataVencimento)
                            .filter(d -> d != null)
                            .max(LocalDate::compareTo)
                            .orElse(null));
        }

        return contaPagarRepository.save(conta);
    }

    /* ------------------------------------------------------------------
       ATUALIZAR (PUT) — estorna itens antigos e processa novos
       ------------------------------------------------------------------ */

    @Transactional
    public ContaPagarEntity atualizar(Long id, ContaPagarRequestDTO dto, Long empresaId) {
        validarDTO(dto);
        ContaPagarEntity conta = buscarPorId(id, empresaId);

        // Estorna estoque dos itens anteriores
        for (ContaPagarItemEntity itemAntigo : conta.getItens()) {
            ProdutoEntity produto = itemAntigo.getProduto();
            produto.setQuantidadeEstoque(
                    produto.getQuantidadeEstoque() - itemAntigo.getQuantidade());
            produtoRepository.save(produto);
        }

        // Limpa as coleções (orphanRemoval cuida da deleção)
        conta.getItens().clear();
        conta.getParcelas().clear();

        // Atualiza campos simples
        conta.setDescricao(dto.getDescricao().trim());
        conta.setFornecedor(dto.getFornecedor() != null ? dto.getFornecedor().trim() : null);
        conta.setNumeroNotaFiscal(dto.getNumeroNotaFiscal() != null ? dto.getNumeroNotaFiscal().trim() : null);
        conta.setPaga(dto.getPaga() != null && dto.getPaga());

        // Reprocesa itens (adiciona novo estoque)
        BigDecimal valorTotal = BigDecimal.ZERO;
        if (dto.getItens() != null && !dto.getItens().isEmpty()) {
            for (ContaPagarItemDTO itemDTO : dto.getItens()) {
                ProdutoEntity produto = produtoRepository.findByIdAndEmpresaId(itemDTO.getCodProduto(), empresaId)
                        .orElseThrow(() -> new RuntimeException(
                                "Produto ID " + itemDTO.getCodProduto() + " não encontrado."));

                if (itemDTO.getQuantidade() == null || itemDTO.getQuantidade() <= 0)
                    throw new IllegalArgumentException("Quantidade inválida para: " + produto.getNome());

                BigDecimal valorUnit = itemDTO.getValorUnitario() != null
                        ? itemDTO.getValorUnitario() : BigDecimal.ZERO;
                BigDecimal totalItem = valorUnit.multiply(new BigDecimal(itemDTO.getQuantidade()));

                ContaPagarItemEntity item = new ContaPagarItemEntity();
                item.setContaPagar(conta);
                item.setProduto(produto);
                item.setQuantidade(itemDTO.getQuantidade());
                item.setValorUnitario(valorUnit);
                item.setValorTotalItem(totalItem);
                conta.getItens().add(item);

                valorTotal = valorTotal.add(totalItem);
                produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + itemDTO.getQuantidade());
                produtoRepository.save(produto);
            }
        }
        conta.setValorTotal(valorTotal);

        if (dto.getParcelas() != null && !dto.getParcelas().isEmpty()) {
            for (ContaPagarParcelaDTO parcelaDTO : dto.getParcelas()) {
                ContaPagarParcelaEntity parcela = new ContaPagarParcelaEntity();
                parcela.setContaPagar(conta);
                parcela.setNumeroParcela(parcelaDTO.getNumeroParcela());
                parcela.setValorParcela(parcelaDTO.getValorParcela() != null
                        ? parcelaDTO.getValorParcela() : BigDecimal.ZERO);
                parcela.setDataVencimento(parcelaDTO.getDataVencimento());
                boolean pagaParcela = parcelaDTO.getPaga() != null
                        ? parcelaDTO.getPaga() : conta.getPaga();
                parcela.setPaga(pagaParcela);
                conta.getParcelas().add(parcela);
            }
            conta.setDataVencimento(
                    conta.getParcelas().stream()
                            .map(ContaPagarParcelaEntity::getDataVencimento)
                            .filter(d -> d != null)
                            .max(LocalDate::compareTo)
                            .orElse(null));
        } else {
            conta.setDataVencimento(null);
        }

        return contaPagarRepository.save(conta);
    }

    /* ------------------------------------------------------------------
       PAGAR (PATCH /pagar) — marca conta e todas as parcelas como pagas
       ------------------------------------------------------------------ */

    @Transactional
    public ContaPagarEntity marcarComoPaga(Long id, Long empresaId) {
        ContaPagarEntity conta = buscarPorId(id, empresaId);
        conta.setPaga(true);
        conta.getParcelas().forEach(p -> p.setPaga(true));
        return contaPagarRepository.save(conta);
    }

    /* ------------------------------------------------------------------
       DELETAR (DELETE) — estorna estoque antes de remover
       ------------------------------------------------------------------ */

    @Transactional
    public void deletar(Long id, Long empresaId) {
        ContaPagarEntity conta = buscarPorId(id, empresaId);

        // Fase 2: estorno de estoque
        for (ContaPagarItemEntity item : conta.getItens()) {
            ProdutoEntity produto = item.getProduto();
            int novoEstoque = produto.getQuantidadeEstoque() - item.getQuantidade();
            produto.setQuantidadeEstoque(Math.max(0, novoEstoque)); // não deixa negativo
            produtoRepository.save(produto);
        }

        contaPagarRepository.delete(conta);
    }

    /* ------------------------------------------------------------------
       VALIDAÇÕES INTERNAS
       ------------------------------------------------------------------ */

    private void validarDTO(ContaPagarRequestDTO dto) {
        if (dto.getDescricao() == null || dto.getDescricao().isBlank()) {
            throw new IllegalArgumentException("O campo 'Descrição/Nome' é obrigatório.");
        }
        if (dto.getParcelas() == null || dto.getParcelas().isEmpty()) {
            throw new IllegalArgumentException("É necessário informar ao menos uma parcela de pagamento.");
        }
        for (ContaPagarParcelaDTO p : dto.getParcelas()) {
            if (p.getValorParcela() == null || p.getValorParcela().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Todas as parcelas devem ter um valor maior que zero.");
            }
            if (!Boolean.TRUE.equals(dto.getPaga()) && p.getDataVencimento() == null) {
                throw new IllegalArgumentException(
                        "Data de vencimento é obrigatória para contas não pagas.");
            }
        }
    }
}
