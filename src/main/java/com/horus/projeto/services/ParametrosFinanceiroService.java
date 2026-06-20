package com.horus.projeto.services;

import com.horus.projeto.entities.ParametrosFinanceirosEntity;
import com.horus.projeto.repositories.ParametrosFinanceirosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuração financeira da empresa (contas padrão). 1 linha por empresa,
 * criada sob demanda. A unicidade do padrão é garantida pelo schema (1 coluna).
 */
@Service
@RequiredArgsConstructor
public class ParametrosFinanceiroService {

    private final ParametrosFinanceirosRepository repo;

    @Transactional
    public ParametrosFinanceirosEntity obter(Long empresaId) {
        return repo.findByEmpresaId(empresaId).orElseGet(() -> {
            ParametrosFinanceirosEntity p = new ParametrosFinanceirosEntity();
            p.setEmpresaId(empresaId);
            return repo.save(p);
        });
    }

    @Transactional
    public void definirCaixaPadrao(Long empresaId, Long codConta) {
        ParametrosFinanceirosEntity p = obter(empresaId);
        p.setCodContaCaixaPadrao(codConta);
        repo.save(p);
    }

    @Transactional
    public void definirBancoPadrao(Long empresaId, Long codConta) {
        ParametrosFinanceirosEntity p = obter(empresaId);
        p.setCodContaBancoPadrao(codConta);
        repo.save(p);
    }

    public Long getCaixaPadrao(Long empresaId) {
        return repo.findByEmpresaId(empresaId).map(ParametrosFinanceirosEntity::getCodContaCaixaPadrao).orElse(null);
    }

    public Long getBancoPadrao(Long empresaId) {
        return repo.findByEmpresaId(empresaId).map(ParametrosFinanceirosEntity::getCodContaBancoPadrao).orElse(null);
    }
}
