package com.horus.projeto.services;

import com.horus.projeto.dto.ConciliacaoResponseDTO;
import com.horus.projeto.dto.MovimentoConciliacaoDTO;
import com.horus.projeto.entities.ContaFinanceiraEntity;
import com.horus.projeto.entities.LancamentoFinanceiroEntity;
import com.horus.projeto.entities.TransferenciaEntity;
import com.horus.projeto.enums.TipoMovimento;
import com.horus.projeto.repositories.ContaFinanceiraRepository;
import com.horus.projeto.repositories.LancamentoFinanceiroRepository;
import com.horus.projeto.repositories.TransferenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Conciliação bancária (marcação manual). Lista os movimentos de uma conta
 * (lançamentos do razão + transferências) e compara Saldo do sistema × Saldo conciliado.
 */
@Service
@RequiredArgsConstructor
public class ConciliacaoService {

    private final ContaFinanceiraRepository contaRepo;
    private final LancamentoFinanceiroRepository lancamentoRepo;
    private final TransferenciaRepository transferenciaRepo;

    public ConciliacaoResponseDTO conciliacaoDaConta(Long empresaId, Long codConta) {
        ContaFinanceiraEntity conta = contaRepo.findByCodContaAndEmpresaId(codConta, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada nesta empresa."));

        ConciliacaoResponseDTO resp = new ConciliacaoResponseDTO();
        resp.setCodConta(codConta);
        resp.setNomeConta(conta.getNome());
        BigDecimal saldoInicial = conta.getSaldoInicial() != null ? conta.getSaldoInicial() : BigDecimal.ZERO;
        resp.setSaldoInicial(saldoInicial);

        List<MovimentoConciliacaoDTO> movs = new ArrayList<>();

        // Lançamentos do razão desta conta
        for (LancamentoFinanceiroEntity l : lancamentoRepo
                .findByEmpresaIdAndCodContaFinanceiraAndEstornadoFalseOrderByDataMovimentoAsc(empresaId, codConta)) {
            BigDecimal v = l.getTipoMovimento() == TipoMovimento.ENTRADA ? l.getValor() : l.getValor().negate();
            movs.add(new MovimentoConciliacaoDTO("LANCAMENTO", l.getCodLancamento(), l.getDataMovimento(),
                    l.getDescricao(), v, Boolean.TRUE.equals(l.getConciliado())));
        }

        // Transferências que tocam esta conta (origem = saída; destino = entrada)
        for (TransferenciaEntity t : transferenciaRepo.findByEmpresaIdAndEstornadoFalse(empresaId)) {
            boolean origem = codConta.equals(t.getCodContaOrigem());
            boolean destino = codConta.equals(t.getCodContaDestino());
            if (!origem && !destino) continue;
            BigDecimal v = destino ? t.getValor() : t.getValor().negate();
            String desc = "Transferência " + (destino ? "recebida" : "enviada")
                    + (t.getDescricao() != null ? " - " + t.getDescricao() : "");
            movs.add(new MovimentoConciliacaoDTO("TRANSFERENCIA", t.getCodTransferencia(), t.getData(),
                    desc, v, Boolean.TRUE.equals(t.getConciliado())));
        }

        movs.sort(Comparator.comparing(MovimentoConciliacaoDTO::getData,
                Comparator.nullsLast(Comparator.naturalOrder())));
        resp.setMovimentos(movs);

        BigDecimal sistema = saldoInicial, conciliado = saldoInicial;
        for (MovimentoConciliacaoDTO m : movs) {
            sistema = sistema.add(m.getValor());
            if (Boolean.TRUE.equals(m.getConciliado())) conciliado = conciliado.add(m.getValor());
        }
        resp.setSaldoSistema(sistema);
        resp.setSaldoConciliado(conciliado);
        resp.setDiferenca(sistema.subtract(conciliado));
        return resp;
    }

    @Transactional
    public void conciliarLancamento(Long empresaId, Long codLancamento, boolean conciliado) {
        LancamentoFinanceiroEntity l = lancamentoRepo.findById(codLancamento)
                .filter(x -> x.getEmpresa() != null && empresaId.equals(x.getEmpresa().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Lançamento não encontrado nesta empresa."));
        l.setConciliado(conciliado);
        l.setDataConciliacao(conciliado ? LocalDate.now() : null);
        lancamentoRepo.save(l);
    }

    @Transactional
    public void conciliarTransferencia(Long empresaId, Long codTransferencia, boolean conciliado) {
        TransferenciaEntity t = transferenciaRepo.findByCodTransferenciaAndEmpresaId(codTransferencia, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Transferência não encontrada nesta empresa."));
        t.setConciliado(conciliado);
        t.setDataConciliacao(conciliado ? LocalDate.now() : null);
        transferenciaRepo.save(t);
    }
}
