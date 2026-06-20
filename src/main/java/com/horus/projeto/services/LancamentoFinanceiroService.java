package com.horus.projeto.services;

import com.horus.projeto.dto.LancamentoManualDTO;
import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.entities.LancamentoFinanceiroEntity;
import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.enums.OrigemLancamento;
import com.horus.projeto.enums.TipoClasse;
import com.horus.projeto.enums.TipoMovimento;
import com.horus.projeto.repositories.ClasseFinanceiraRepository;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.LancamentoFinanceiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Razão financeiro: posta entradas/saídas (sempre em classe ANALÍTICA) e estorna.
 * É o ponto único por onde TODO movimento de caixa entra no sistema.
 */
@Service
@RequiredArgsConstructor
public class LancamentoFinanceiroService {

    private final LancamentoFinanceiroRepository lancamentoRepo;
    private final ClasseFinanceiraRepository classeRepo;
    private final EmpresaRepository empresaRepository;

    /** Posta um lançamento já classificado. Usado pela venda, pelo pagamento de parcela e pelo manual. */
    @Transactional
    public LancamentoFinanceiroEntity postar(Long empresaId, Long codClasse, BigDecimal valor,
                                             LocalDate dataMovimento, String descricao,
                                             OrigemLancamento origem, Long origemId, Long codContaFinanceira) {
        if (valor == null || valor.signum() <= 0)
            throw new IllegalArgumentException("O valor do lançamento deve ser positivo.");
        if (dataMovimento == null)
            throw new IllegalArgumentException("A data do movimento é obrigatória.");

        ClasseFinanceiraEntity classe = classeRepo.findByCodClasseAndEmpresaId(codClasse, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Classe financeira não encontrada nesta empresa."));

        // TRAVA central: sintética nunca recebe lançamento
        if (classe.getNivel() != NivelClasse.ANALITICA)
            throw new IllegalArgumentException("Lançamentos só podem ser feitos em classes ANALÍTICAS.");

        return construirESalvar(empresaId, classe, valor, dataMovimento, descricao, origem, origemId, codContaFinanceira);
    }

    /**
     * Versão best-effort para o PDV: retorna {@code null} (em vez de lançar exceção) quando
     * os dados são inválidos. Evita marcar a transação da venda como rollback-only — uma
     * classificação ausente/errada NUNCA pode derrubar a venda.
     */
    @Transactional
    public LancamentoFinanceiroEntity postarSeValido(Long empresaId, Long codClasse, BigDecimal valor,
                                                     LocalDate dataMovimento, String descricao,
                                                     OrigemLancamento origem, Long origemId, Long codContaFinanceira) {
        if (codClasse == null || valor == null || valor.signum() <= 0 || dataMovimento == null) return null;
        ClasseFinanceiraEntity classe = classeRepo.findByCodClasseAndEmpresaId(codClasse, empresaId).orElse(null);
        if (classe == null || classe.getNivel() != NivelClasse.ANALITICA) return null;
        return construirESalvar(empresaId, classe, valor, dataMovimento, descricao, origem, origemId, codContaFinanceira);
    }

    private LancamentoFinanceiroEntity construirESalvar(Long empresaId, ClasseFinanceiraEntity classe,
                                                        BigDecimal valor, LocalDate dataMovimento, String descricao,
                                                        OrigemLancamento origem, Long origemId, Long codContaFinanceira) {
        LancamentoFinanceiroEntity l = new LancamentoFinanceiroEntity();
        l.setEmpresa(empresaRepository.getReferenceById(empresaId));
        l.setCodClasse(classe.getCodClasse());
        l.setCodContaFinanceira(codContaFinanceira);
        l.setTipoMovimento(classe.getTipo() == TipoClasse.RECEITA ? TipoMovimento.ENTRADA : TipoMovimento.SAIDA);
        l.setValor(valor);
        l.setDataMovimento(dataMovimento);
        l.setDescricao(descricao);
        l.setOrigem(origem);
        l.setOrigemId(origemId);
        l.setEstornado(false);
        return lancamentoRepo.save(l);
    }

    @Transactional
    public LancamentoFinanceiroEntity postarManual(Long empresaId, LancamentoManualDTO dto) {
        return postar(empresaId, dto.getCodClasse(), dto.getValor(), dto.getDataMovimento(),
                dto.getDescricao(), OrigemLancamento.MANUAL, null, null);
    }

    /** Estorno por flag — o lançamento sai do DFC (a query soma só estornado=false). */
    @Transactional
    public void estornar(Long empresaId, Long codLancamento) {
        LancamentoFinanceiroEntity l = lancamentoRepo.findById(codLancamento)
                .filter(x -> x.getEmpresa() != null && empresaId.equals(x.getEmpresa().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Lançamento não encontrado nesta empresa."));
        if (Boolean.TRUE.equals(l.getEstornado()))
            throw new IllegalArgumentException("Este lançamento já está estornado.");
        l.setEstornado(true);
        lancamentoRepo.save(l);
    }

    /** Estorna todos os lançamentos ativos de uma origem (tipo + id) — escopo seguro. */
    @Transactional
    public void estornarPorOrigem(OrigemLancamento origem, Long origemId) {
        lancamentoRepo.findByOrigemAndOrigemIdAndEstornadoFalse(origem, origemId).forEach(l -> {
            l.setEstornado(true);
            lancamentoRepo.save(l);
        });
    }
}
