package com.horus.projeto.services;

import com.horus.projeto.dto.ContaPagarItemDTO;
import com.horus.projeto.dto.ContaPagarParcelaDTO;
import com.horus.projeto.dto.ContaPagarRequestDTO;
import com.horus.projeto.entities.*;
import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.enums.OrigemLancamento;
import com.horus.projeto.enums.TipoClasse;
import com.horus.projeto.repositories.ClasseFinanceiraRepository;
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
    private final LancamentoFinanceiroService lancamentoService;
    private final ClasseFinanceiraRepository classeRepo;
    private final com.horus.projeto.repositories.ContaFinanceiraRepository contaFinanceiraRepo;

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
        validarClasseConta(empresaId, dto.getCodClasse());
        conta.setCodClasse(dto.getCodClasse());
        validarContaSaida(empresaId, dto.getCodContaFinanceira());
        conta.setCodContaFinanceira(dto.getCodContaFinanceira());

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
                definirDataPagamentoSePaga(parcela);
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

        ContaPagarEntity salva = contaPagarRepository.save(conta);
        sincronizarFinanceiro(salva, empresaId);
        return salva;
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
        validarClasseConta(empresaId, dto.getCodClasse());
        conta.setCodClasse(dto.getCodClasse());
        validarContaSaida(empresaId, dto.getCodContaFinanceira());
        conta.setCodContaFinanceira(dto.getCodContaFinanceira());

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
                definirDataPagamentoSePaga(parcela);
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

        ContaPagarEntity salva = contaPagarRepository.save(conta);
        sincronizarFinanceiro(salva, empresaId);
        return salva;
    }

    /* ------------------------------------------------------------------
       PAGAR (PATCH /pagar) — marca conta e todas as parcelas como pagas
       ------------------------------------------------------------------ */

    @Transactional
    public ContaPagarEntity marcarComoPaga(Long id, Long empresaId, Long codContaFinanceira) {
        ContaPagarEntity conta = buscarPorId(id, empresaId);
        validarContaSaida(empresaId, codContaFinanceira);
        if (codContaFinanceira != null) conta.setCodContaFinanceira(codContaFinanceira);
        conta.setPaga(true);
        conta.getParcelas().forEach(p -> {
            p.setPaga(true);
            if (p.getDataPagamento() == null) p.setDataPagamento(LocalDate.now());
        });
        ContaPagarEntity salva = contaPagarRepository.save(conta);
        sincronizarFinanceiro(salva, empresaId);
        return salva;
    }

    /* ------------------------------------------------------------------
       DELETAR (DELETE) — estorna estoque antes de remover
       ------------------------------------------------------------------ */

    @Transactional
    public void deletar(Long id, Long empresaId) {
        ContaPagarEntity conta = buscarPorId(id, empresaId);

        // Estorno financeiro: as saídas geradas por esta conta saem do DFC
        lancamentoService.estornarPorOrigem(OrigemLancamento.CONTA_PAGAR, conta.getCodContaPagar());

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
       INTEGRAÇÃO FINANCEIRA (razão / DFC)
       ------------------------------------------------------------------ */

    /** Conta a Pagar só pode usar classe ANALÍTICA de CUSTO ou DESPESA. */
    private void validarClasseConta(Long empresaId, Long codClasse) {
        if (codClasse == null) return; // classificação é opcional
        ClasseFinanceiraEntity classe = classeRepo.findByCodClasseAndEmpresaId(codClasse, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Classe financeira não encontrada nesta empresa."));
        if (classe.getNivel() != NivelClasse.ANALITICA)
            throw new IllegalArgumentException("A conta deve ser vinculada a uma classe ANALÍTICA.");
        if (classe.getTipo() == TipoClasse.RECEITA)
            throw new IllegalArgumentException("Conta a Pagar deve usar uma classe de CUSTO ou DESPESA.");
    }

    private void definirDataPagamentoSePaga(ContaPagarParcelaEntity p) {
        if (Boolean.TRUE.equals(p.getPaga()) && p.getDataPagamento() == null) {
            p.setDataPagamento(p.getDataVencimento() != null ? p.getDataVencimento() : LocalDate.now());
        }
    }

    /** Conta de saída: opcional no cadastro de título a vencer, mas obrigatória na baixa (validada em sincronizar). */
    private void validarContaSaida(Long empresaId, Long codContaFinanceira) {
        if (codContaFinanceira == null) return;
        var conta = contaFinanceiraRepo.findByCodContaAndEmpresaId(codContaFinanceira, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conta financeira não encontrada nesta empresa."));
        if (!Boolean.TRUE.equals(conta.getAtivo()))
            throw new IllegalArgumentException("A conta financeira selecionada está inativa.");
    }

    /**
     * Re-sincroniza as saídas do razão para esta conta: estorna as atuais e reposta
     * uma SAÍDA por parcela PAGA (na data de pagamento, debitando a conta financeira).
     * Idempotente. Exige a conta de saída quando há parcela paga (regra da baixa).
     */
    private void sincronizarFinanceiro(ContaPagarEntity conta, Long empresaId) {
        lancamentoService.estornarPorOrigem(OrigemLancamento.CONTA_PAGAR, conta.getCodContaPagar());

        boolean temParcelaPaga = conta.getParcelas().stream().anyMatch(p -> Boolean.TRUE.equals(p.getPaga()));
        if (!temParcelaPaga) return; // nada pago → nada a lançar

        if (conta.getCodContaFinanceira() == null)
            throw new IllegalArgumentException("Informe a conta financeira de onde o dinheiro saiu para pagar.");

        if (conta.getCodClasse() == null) return; // sem classe não classifica (conta já foi exigida)

        for (ContaPagarParcelaEntity p : conta.getParcelas()) {
            if (!Boolean.TRUE.equals(p.getPaga())) continue;
            LocalDate dataPag = p.getDataPagamento() != null ? p.getDataPagamento()
                    : (p.getDataVencimento() != null ? p.getDataVencimento() : LocalDate.now());
            lancamentoService.postar(empresaId, conta.getCodClasse(), p.getValorParcela(), dataPag,
                    "Pagamento: " + conta.getDescricao() + " (parc. " + p.getNumeroParcela() + ")",
                    OrigemLancamento.CONTA_PAGAR, conta.getCodContaPagar(), conta.getCodContaFinanceira());
        }
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
