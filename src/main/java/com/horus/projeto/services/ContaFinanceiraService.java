package com.horus.projeto.services;

import com.horus.projeto.dto.ContaFinanceiraRequestDTO;
import com.horus.projeto.entities.ContaFinanceiraEntity;
import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.enums.TipoConta;
import com.horus.projeto.repositories.ContaFinanceiraRepository;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.LancamentoFinanceiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contas Financeiras — CRUD + travas. As contas padrão são designadas via
 * ParametrosFinanceiroService (a unicidade é estrutural, não há flag na conta).
 */
@Service
@RequiredArgsConstructor
public class ContaFinanceiraService {

    private final ContaFinanceiraRepository contaRepo;
    private final LancamentoFinanceiroRepository lancamentoRepo;
    private final EmpresaRepository empresaRepository;
    private final ParametrosFinanceiroService parametrosService;
    private final com.horus.projeto.repositories.TransferenciaRepository transferenciaRepo;

    @Transactional
    public List<ContaFinanceiraEntity> listar(Long empresaId) {
        seedSeVazio(empresaId); // garante Caixa/Banco padrão na 1ª utilização
        return contaRepo.findByEmpresaIdOrderByNomeAsc(empresaId);
    }

    /** Cria Caixa e Banco padrão se a empresa ainda não tiver nenhuma conta. */
    @Transactional
    public void seedSeVazio(Long empresaId) {
        if (contaRepo.countByEmpresaId(empresaId) > 0) return;
        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);
        ContaFinanceiraEntity caixa = salvarNova(empresa, "Caixa", TipoConta.CAIXA, BigDecimal.ZERO);
        ContaFinanceiraEntity banco = salvarNova(empresa, "Banco", TipoConta.BANCO, BigDecimal.ZERO);
        parametrosService.definirCaixaPadrao(empresaId, caixa.getCodConta());
        parametrosService.definirBancoPadrao(empresaId, banco.getCodConta());
    }

    /** Saldo ATUAL de cada conta = saldo inicial + razão da conta (± estornos) ± transferências. */
    public java.util.Map<Long, java.math.BigDecimal> saldosAtuais(Long empresaId) {
        java.util.Map<Long, java.math.BigDecimal> saldos = new java.util.HashMap<>();
        for (ContaFinanceiraEntity c : contaRepo.findByEmpresaIdOrderByNomeAsc(empresaId))
            saldos.put(c.getCodConta(), c.getSaldoInicial() != null ? c.getSaldoInicial() : java.math.BigDecimal.ZERO);
        for (Object[] row : lancamentoRepo.somarAssinadoPorConta(empresaId)) {
            Long conta = (Long) row[0];
            if (conta != null && saldos.containsKey(conta))
                saldos.merge(conta, (java.math.BigDecimal) row[1], java.math.BigDecimal::add);
        }
        for (com.horus.projeto.entities.TransferenciaEntity t : transferenciaRepo.findByEmpresaIdAndEstornadoFalse(empresaId)) {
            if (saldos.containsKey(t.getCodContaDestino()))
                saldos.merge(t.getCodContaDestino(), t.getValor(), java.math.BigDecimal::add);
            if (saldos.containsKey(t.getCodContaOrigem()))
                saldos.merge(t.getCodContaOrigem(), t.getValor().negate(), java.math.BigDecimal::add);
        }
        return saldos;
    }

    @Transactional
    public ContaFinanceiraEntity criar(Long empresaId, ContaFinanceiraRequestDTO dto) {
        validar(dto);
        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);
        ContaFinanceiraEntity conta = salvarNova(empresa, dto.getNome().trim(), dto.getTipoConta(),
                dto.getSaldoInicial() != null ? dto.getSaldoInicial() : BigDecimal.ZERO);
        autodesignarPadraoSeVazio(empresaId, conta); // 1ª conta do tipo vira padrão
        return conta;
    }

    @Transactional
    public ContaFinanceiraEntity atualizar(Long empresaId, Long id, ContaFinanceiraRequestDTO dto) {
        validar(dto);
        ContaFinanceiraEntity conta = buscar(empresaId, id);
        conta.setNome(dto.getNome().trim());
        if (dto.getTipoConta() != null) conta.setTipoConta(dto.getTipoConta());
        if (dto.getSaldoInicial() != null) conta.setSaldoInicial(dto.getSaldoInicial());
        return contaRepo.save(conta);
    }

    @Transactional
    public ContaFinanceiraEntity alternarAtivo(Long empresaId, Long id) {
        ContaFinanceiraEntity conta = buscar(empresaId, id);
        if (Boolean.TRUE.equals(conta.getAtivo()) && ehPadrao(empresaId, id)) {
            throw new IllegalArgumentException("Esta conta é padrão. Defina outra como padrão antes de inativá-la.");
        }
        conta.setAtivo(!Boolean.TRUE.equals(conta.getAtivo()));
        return contaRepo.save(conta);
    }

    @Transactional
    public void deletar(Long empresaId, Long id) {
        ContaFinanceiraEntity conta = buscar(empresaId, id);
        if (ehPadrao(empresaId, id)) {
            throw new IllegalArgumentException("Esta conta é padrão. Defina outra como padrão antes de excluí-la.");
        }
        if (lancamentoRepo.existsByCodContaFinanceira(id)) {
            throw new IllegalArgumentException("Esta conta possui movimentações. Inative-a em vez de excluir.");
        }
        if (transferenciaRepo.existsByCodContaOrigemOrCodContaDestino(id, id)) {
            throw new IllegalArgumentException("Esta conta possui transferências. Inative-a em vez de excluir.");
        }
        contaRepo.delete(conta);
    }

    @Transactional
    public void definirPadrao(Long empresaId, Long id) {
        ContaFinanceiraEntity conta = buscar(empresaId, id);
        if (!Boolean.TRUE.equals(conta.getAtivo())) {
            throw new IllegalArgumentException("Não é possível usar uma conta inativa como padrão.");
        }
        if (conta.getTipoConta() == TipoConta.CAIXA) parametrosService.definirCaixaPadrao(empresaId, id);
        else parametrosService.definirBancoPadrao(empresaId, id);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ContaFinanceiraEntity salvarNova(EmpresaEntity empresa, String nome, TipoConta tipo, BigDecimal saldo) {
        ContaFinanceiraEntity c = new ContaFinanceiraEntity();
        c.setEmpresa(empresa);
        c.setNome(nome);
        c.setTipoConta(tipo);
        c.setSaldoInicial(saldo);
        c.setAtivo(true);
        return contaRepo.save(c);
    }

    private void autodesignarPadraoSeVazio(Long empresaId, ContaFinanceiraEntity conta) {
        if (conta.getTipoConta() == TipoConta.CAIXA && parametrosService.getCaixaPadrao(empresaId) == null)
            parametrosService.definirCaixaPadrao(empresaId, conta.getCodConta());
        if (conta.getTipoConta() == TipoConta.BANCO && parametrosService.getBancoPadrao(empresaId) == null)
            parametrosService.definirBancoPadrao(empresaId, conta.getCodConta());
    }

    private boolean ehPadrao(Long empresaId, Long id) {
        return id.equals(parametrosService.getCaixaPadrao(empresaId))
                || id.equals(parametrosService.getBancoPadrao(empresaId));
    }

    private ContaFinanceiraEntity buscar(Long empresaId, Long id) {
        return contaRepo.findByCodContaAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conta financeira não encontrada nesta empresa."));
    }

    private void validar(ContaFinanceiraRequestDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank())
            throw new IllegalArgumentException("Nome da conta é obrigatório.");
        if (dto.getTipoConta() == null)
            throw new IllegalArgumentException("Tipo da conta (CAIXA ou BANCO) é obrigatório.");
    }
}
