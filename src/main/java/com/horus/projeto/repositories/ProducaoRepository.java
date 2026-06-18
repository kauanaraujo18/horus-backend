package com.horus.projeto.repositories;

import com.horus.projeto.entities.ProducaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProducaoRepository extends JpaRepository<ProducaoEntity, Long> {

    List<ProducaoEntity> findByEmpresaIdOrderByDataProducaoDesc(Long empresaId);

    Optional<ProducaoEntity> findByCodProducaoAndEmpresaId(Long codProducao, Long empresaId);
}
