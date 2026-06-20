package com.horus.projeto.services;

import com.horus.projeto.dto.ClasseFinanceiraRequestDTO;
import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.enums.NivelClasse;
import com.horus.projeto.repositories.ClasseFinanceiraRepository;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.LancamentoFinanceiroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Plano de Contas — CRUD com TODAS as travas de integridade financeira.
 */
@Service
@RequiredArgsConstructor
public class ClasseFinanceiraService {

    private final ClasseFinanceiraRepository classeRepo;
    private final LancamentoFinanceiroRepository lancamentoRepo;
    private final EmpresaRepository empresaRepository;
    private final PlanoContasSeeder planoContasSeeder;

    public List<ClasseFinanceiraEntity> listar(Long empresaId) {
        // Bootstrap idempotente: na 1ª abertura, semeia o plano padrão se a empresa não tiver nenhum.
        planoContasSeeder.semearSeVazio(empresaId);
        return classeRepo.findByEmpresaIdOrderByCodigoAscNomeAsc(empresaId);
    }

    /** Apenas analíticas ATIVAS — alimenta os seletores de Produto e Conta a Pagar. */
    public List<ClasseFinanceiraEntity> listarAnaliticas(Long empresaId) {
        planoContasSeeder.semearSeVazio(empresaId); // garante o plano padrão na 1ª utilização
        return classeRepo
                .findByEmpresaIdAndNivelOrderByCodigoAscNomeAsc(empresaId, NivelClasse.ANALITICA)
                .stream().filter(c -> Boolean.TRUE.equals(c.getAtivo())).toList();
    }

    @Transactional
    public ClasseFinanceiraEntity criar(Long empresaId, ClasseFinanceiraRequestDTO dto) {
        validarBasico(dto);
        ClasseFinanceiraEntity classe = new ClasseFinanceiraEntity();
        classe.setEmpresa(empresaRepository.getReferenceById(empresaId));
        classe.setNome(dto.getNome().trim());
        classe.setTipo(dto.getTipo());
        classe.setNivel(dto.getNivel());
        classe.setAtivo(true);
        aplicarPai(empresaId, classe, dto.getCodClassePai(), null);
        classe.setCodigo((dto.getCodigo() != null && !dto.getCodigo().isBlank())
                ? dto.getCodigo().trim()
                : gerarCodigo(empresaId, dto.getCodClassePai()));
        return classeRepo.save(classe);
    }

    @Transactional
    public ClasseFinanceiraEntity atualizar(Long empresaId, Long id, ClasseFinanceiraRequestDTO dto) {
        validarBasico(dto);
        ClasseFinanceiraEntity classe = buscar(empresaId, id);

        // Trava: mudança de nível só se a classe ainda não estiver em uso
        if (classe.getNivel() == NivelClasse.ANALITICA && dto.getNivel() == NivelClasse.SINTETICA
                && lancamentoRepo.existsByCodClasse(id)) {
            throw new IllegalArgumentException("Esta classe analítica já possui lançamentos e não pode virar sintética.");
        }
        if (classe.getNivel() == NivelClasse.SINTETICA && dto.getNivel() == NivelClasse.ANALITICA
                && classeRepo.existsByCodClassePai(id)) {
            throw new IllegalArgumentException("Esta classe sintética possui filhos e não pode virar analítica.");
        }

        classe.setNome(dto.getNome().trim());
        classe.setTipo(dto.getTipo());
        classe.setNivel(dto.getNivel());
        if (dto.getCodigo() != null && !dto.getCodigo().isBlank()) classe.setCodigo(dto.getCodigo().trim());
        aplicarPai(empresaId, classe, dto.getCodClassePai(), id);
        return classeRepo.save(classe);
    }

    @Transactional
    public ClasseFinanceiraEntity alternarAtivo(Long empresaId, Long id) {
        ClasseFinanceiraEntity classe = buscar(empresaId, id);
        classe.setAtivo(!Boolean.TRUE.equals(classe.getAtivo()));
        return classeRepo.save(classe);
    }

    @Transactional
    public void deletar(Long empresaId, Long id) {
        ClasseFinanceiraEntity classe = buscar(empresaId, id);
        if (classeRepo.existsByCodClassePai(id)) {
            throw new IllegalArgumentException("Esta classe possui filhos. Mova-os ou apenas inative esta classe.");
        }
        if (lancamentoRepo.existsByCodClasse(id)) {
            throw new IllegalArgumentException("Esta classe possui lançamentos no histórico. Inative-a em vez de excluir.");
        }
        classeRepo.delete(classe);
    }

    // ── Helpers / travas ────────────────────────────────────────────────────

    private ClasseFinanceiraEntity buscar(Long empresaId, Long id) {
        return classeRepo.findByCodClasseAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Classe financeira não encontrada nesta empresa."));
    }

    private void validarBasico(ClasseFinanceiraRequestDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank())
            throw new IllegalArgumentException("Nome da classe é obrigatório.");
        if (dto.getTipo() == null)
            throw new IllegalArgumentException("Tipo (RECEITA/CUSTO/DESPESA) é obrigatório.");
        if (dto.getNivel() == null)
            throw new IllegalArgumentException("Nível (SINTETICA/ANALITICA) é obrigatório.");
    }

    /**
     * Aplica o pai com todas as travas: pai existe na empresa, é SINTÉTICA, mesmo tipo,
     * e (quando edição) não cria ciclo. {@code idAtual} = id em edição, ou null em criação.
     */
    private void aplicarPai(Long empresaId, ClasseFinanceiraEntity classe, Long codPai, Long idAtual) {
        if (codPai == null) { classe.setCodClassePai(null); return; }
        if (codPai.equals(idAtual))
            throw new IllegalArgumentException("Uma classe não pode ser pai de si mesma.");

        ClasseFinanceiraEntity pai = buscar(empresaId, codPai);
        if (pai.getNivel() != NivelClasse.SINTETICA)
            throw new IllegalArgumentException("A classe pai precisa ser SINTÉTICA.");
        if (pai.getTipo() != classe.getTipo())
            throw new IllegalArgumentException("A classe deve ter o mesmo tipo do pai (" + pai.getTipo() + ").");

        // Anti-ciclo: subindo a cadeia do novo pai não pode reencontrar a própria classe
        if (idAtual != null) {
            Long cursor = codPai;
            int guarda = 0;
            while (cursor != null && guarda++ < 1000) {
                if (cursor.equals(idAtual))
                    throw new IllegalArgumentException("Movimentação inválida: criaria um ciclo na árvore.");
                cursor = classeRepo.findById(cursor).map(ClasseFinanceiraEntity::getCodClassePai).orElse(null);
            }
        }
        classe.setCodClassePai(codPai);
    }

    private String gerarCodigo(Long empresaId, Long codPai) {
        if (codPai == null) {
            long n = classeRepo.countByEmpresaIdAndCodClassePaiIsNull(empresaId) + 1;
            return String.valueOf(n);
        }
        ClasseFinanceiraEntity pai = classeRepo.findById(codPai).orElse(null);
        String base = (pai != null && pai.getCodigo() != null) ? pai.getCodigo() : "";
        long n = classeRepo.countByCodClassePai(codPai) + 1;
        return base.isBlank() ? String.valueOf(n) : base + "." + String.format("%02d", n);
    }
}
