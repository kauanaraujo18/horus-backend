package com.horus.projeto.services;

import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.enums.TipoClasse;
import com.horus.projeto.repositories.ClasseFinanceiraRepository;
import com.horus.projeto.repositories.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semeia um Plano de Contas padrão de varejo para uma empresa que ainda não tem nenhum.
 * Idempotente: só age quando a empresa tem zero classes.
 */
@Service
@RequiredArgsConstructor
public class PlanoContasSeeder {

    private final ClasseFinanceiraRepository classeRepo;
    private final EmpresaRepository empresaRepository;

    @Transactional
    public void semearSeVazio(Long empresaId) {
        if (classeRepo.countByEmpresaId(empresaId) > 0) return;
        EmpresaEntity empresa = empresaRepository.getReferenceById(empresaId);

        // ── RECEITAS ─────────────────────────────────────────────────────────
        ClasseFinanceiraEntity receitas = sintetica(empresa, null, TipoClasse.RECEITA, "Receitas", "3");
        ClasseFinanceiraEntity recOper  = sintetica(empresa, receitas, TipoClasse.RECEITA, "Receitas Operacionais", "3.1");
        analitica(empresa, recOper, "Vendas de Mercadorias", "3.1.01");
        analitica(empresa, recOper, "Outras Receitas",       "3.1.02");

        // ── CUSTOS ───────────────────────────────────────────────────────────
        ClasseFinanceiraEntity custos      = sintetica(empresa, null, TipoClasse.CUSTO, "Custos", "4");
        ClasseFinanceiraEntity custoVendas = sintetica(empresa, custos, TipoClasse.CUSTO, "Custos das Vendas", "4.1");
        analitica(empresa, custoVendas, "Compras de Mercadorias",               "4.1.01");
        analitica(empresa, custoVendas, "Custo das Mercadorias Vendidas (CMV)", "4.1.02");

        // ── DESPESAS ─────────────────────────────────────────────────────────
        ClasseFinanceiraEntity despesas = sintetica(empresa, null, TipoClasse.DESPESA, "Despesas", "5");
        ClasseFinanceiraEntity despOper = sintetica(empresa, despesas, TipoClasse.DESPESA, "Despesas Operacionais", "5.1");
        analitica(empresa, despOper, "Aluguel",                  "5.1.01");
        analitica(empresa, despOper, "Água / Energia / Internet","5.1.02");
        analitica(empresa, despOper, "Salários e Encargos",      "5.1.03");
        analitica(empresa, despOper, "Impostos e Taxas",         "5.1.04");
        analitica(empresa, despOper, "Despesas Administrativas", "5.1.05");
        analitica(empresa, despOper, "Outras Despesas",          "5.1.06");
    }

    private ClasseFinanceiraEntity sintetica(EmpresaEntity empresa, ClasseFinanceiraEntity pai,
                                             TipoClasse tipo, String nome, String codigo) {
        return salvar(empresa, pai, tipo, NivelClasse.SINTETICA, nome, codigo);
    }

    private ClasseFinanceiraEntity analitica(EmpresaEntity empresa, ClasseFinanceiraEntity pai,
                                             String nome, String codigo) {
        return salvar(empresa, pai, pai.getTipo(), NivelClasse.ANALITICA, nome, codigo);
    }

    private ClasseFinanceiraEntity salvar(EmpresaEntity empresa, ClasseFinanceiraEntity pai,
                                          TipoClasse tipo, NivelClasse nivel, String nome, String codigo) {
        ClasseFinanceiraEntity c = new ClasseFinanceiraEntity();
        c.setEmpresa(empresa);
        c.setCodClassePai(pai != null ? pai.getCodClasse() : null);
        c.setTipo(tipo);
        c.setNivel(nivel);
        c.setNome(nome);
        c.setCodigo(codigo);
        c.setAtivo(true);
        return classeRepo.save(c);
    }
}
