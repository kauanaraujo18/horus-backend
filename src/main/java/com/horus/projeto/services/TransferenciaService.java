package com.horus.projeto.services;

import com.horus.projeto.dto.TransferenciaRequestDTO;
import com.horus.projeto.entities.ContaFinanceiraEntity;
import com.horus.projeto.entities.TransferenciaEntity;
import com.horus.projeto.repositories.ContaFinanceiraRepository;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.TransferenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Transferência de saldo entre contas próprias. Neutra ao DFC — só remaneja saldo
 * entre as contas (− origem, + destino). Não gera lançamento no razão.
 */
@Service
@RequiredArgsConstructor
public class TransferenciaService {

    private final TransferenciaRepository transferenciaRepo;
    private final ContaFinanceiraRepository contaRepo;
    private final EmpresaRepository empresaRepository;

    public List<TransferenciaEntity> listar(Long empresaId) {
        return transferenciaRepo.findByEmpresaIdOrderByDataDescCodTransferenciaDesc(empresaId);
    }

    @Transactional
    public TransferenciaEntity criar(Long empresaId, TransferenciaRequestDTO dto) {
        if (dto.getValor() == null || dto.getValor().signum() <= 0)
            throw new IllegalArgumentException("O valor da transferência deve ser positivo.");
        if (dto.getCodContaOrigem() == null || dto.getCodContaDestino() == null)
            throw new IllegalArgumentException("Informe a conta de origem e a de destino.");
        if (dto.getCodContaOrigem().equals(dto.getCodContaDestino()))
            throw new IllegalArgumentException("A conta de origem e destino não podem ser a mesma.");

        ContaFinanceiraEntity origem = buscarConta(empresaId, dto.getCodContaOrigem());
        ContaFinanceiraEntity destino = buscarConta(empresaId, dto.getCodContaDestino());
        if (!Boolean.TRUE.equals(origem.getAtivo()) || !Boolean.TRUE.equals(destino.getAtivo()))
            throw new IllegalArgumentException("Não é possível transferir usando uma conta inativa.");

        TransferenciaEntity t = new TransferenciaEntity();
        t.setEmpresa(empresaRepository.getReferenceById(empresaId));
        t.setCodContaOrigem(dto.getCodContaOrigem());
        t.setCodContaDestino(dto.getCodContaDestino());
        t.setValor(dto.getValor());
        t.setData(dto.getData() != null ? dto.getData() : LocalDate.now());
        t.setDescricao(dto.getDescricao());
        t.setEstornado(false);
        return transferenciaRepo.save(t);
    }

    @Transactional
    public void estornar(Long empresaId, Long codTransferencia) {
        TransferenciaEntity t = transferenciaRepo.findByCodTransferenciaAndEmpresaId(codTransferencia, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Transferência não encontrada nesta empresa."));
        if (Boolean.TRUE.equals(t.getEstornado()))
            throw new IllegalArgumentException("Esta transferência já está estornada.");
        t.setEstornado(true);
        transferenciaRepo.save(t);
    }

    private ContaFinanceiraEntity buscarConta(Long empresaId, Long codConta) {
        return contaRepo.findByCodContaAndEmpresaId(codConta, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Conta financeira não encontrada nesta empresa."));
    }
}
