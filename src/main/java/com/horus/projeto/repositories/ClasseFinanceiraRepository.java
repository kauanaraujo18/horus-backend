package com.horus.projeto.repositories;

import com.horus.projeto.entities.ClasseFinanceiraEntity;
import com.horus.projeto.enums.NivelClasse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClasseFinanceiraRepository extends JpaRepository<ClasseFinanceiraEntity, Long> {

    List<ClasseFinanceiraEntity> findByEmpresaIdOrderByCodigoAscNomeAsc(Long empresaId);

    List<ClasseFinanceiraEntity> findByEmpresaIdAndNivelOrderByCodigoAscNomeAsc(Long empresaId, NivelClasse nivel);

    Optional<ClasseFinanceiraEntity> findByCodClasseAndEmpresaId(Long codClasse, Long empresaId);

    /** Guarda de exclusão: a classe possui filhos? (sintética não pode ser apagada com filhos) */
    boolean existsByCodClassePai(Long codClassePai);

    long countByEmpresaId(Long empresaId);

    // Suporte à geração do código hierárquico automático
    long countByEmpresaIdAndCodClassePaiIsNull(Long empresaId);

    long countByCodClassePai(Long codClassePai);
}
