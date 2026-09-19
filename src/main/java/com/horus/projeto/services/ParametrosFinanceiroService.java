package com.horus.projeto.services;

import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.entities.ParametrosFinanceirosEntity;
import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.enums.TipoClasse;
import com.horus.projeto.repositories.ClasseFinanceiraRepository;
import com.horus.projeto.repositories.ParametrosFinanceirosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuração financeira da empresa (contas padrão + classe de CMV). 1 linha por
 * empresa, criada sob demanda. A unicidade do padrão é garantida pelo schema (1 coluna).
 */
@Service
@RequiredArgsConstructor
public class ParametrosFinanceiroService {

    /** Código da classe "Custo das Mercadorias Vendidas (CMV)" no plano padrão do seeder. */
    private static final String CODIGO_CMV_PADRAO = "4.1.02";

    private final ParametrosFinanceirosRepository repo;
    private final ClasseFinanceiraRepository classeRepo;

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

    // ── Classe de CMV ────────────────────────────────────────────────────────

    /**
     * Define manualmente a classe analítica que receberá o CMV.
     * Só aceita classe ANALÍTICA do tipo CUSTO da própria empresa — a mesma trava
     * conceitual do razão, onde sintética nunca recebe valor.
     */
    @Transactional
    public void definirClasseCmv(Long empresaId, Long codClasse) {
        if (codClasse != null) {
            ClasseFinanceiraEntity classe = classeRepo.findByCodClasseAndEmpresaId(codClasse, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Classe não encontrada nesta empresa."));
            if (classe.getNivel() != NivelClasse.ANALITICA)
                throw new IllegalArgumentException("A classe de CMV deve ser ANALÍTICA.");
            if (classe.getTipo() != TipoClasse.CUSTO)
                throw new IllegalArgumentException("A classe de CMV deve ser do tipo CUSTO.");
        }
        ParametrosFinanceirosEntity p = obter(empresaId);
        p.setCodClasseCmv(codClasse);
        repo.save(p);
    }

    /**
     * Classe analítica de CUSTO em que o CMV é classificado.
     * Resolve em cascata: parâmetro explícito -> código "4.1.02" do plano padrão ->
     * primeira analítica de CUSTO cujo nome contenha "CMV" -> null.
     *
     * Retornar null NÃO impede a apuração do CMV: a linha é gravada sem classe e a
     * margem continua correta; apenas a quebra da DRE por classe fica indisponível.
     */
    public Long getClasseCmv(Long empresaId) {
        Long configurada = repo.findByEmpresaId(empresaId)
                .map(ParametrosFinanceirosEntity::getCodClasseCmv).orElse(null);
        if (configurada != null) return configurada;

        return classeRepo.findByEmpresaIdAndNivelOrderByCodigoAscNomeAsc(empresaId, NivelClasse.ANALITICA)
                .stream()
                .filter(c -> c.getTipo() == TipoClasse.CUSTO)
                .filter(c -> CODIGO_CMV_PADRAO.equals(c.getCodigo())
                        || (c.getNome() != null && c.getNome().toUpperCase().contains("CMV")))
                .map(ClasseFinanceiraEntity::getCodClasse)
                .findFirst()
                .orElse(null);
    }
}
