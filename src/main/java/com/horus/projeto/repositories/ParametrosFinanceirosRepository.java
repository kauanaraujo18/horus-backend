package com.horus.projeto.repositories;

import com.horus.projeto.entities.ParametrosFinanceirosEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParametrosFinanceirosRepository extends JpaRepository<ParametrosFinanceirosEntity, Long> {

    // A PK já é o empresa_id; este alias deixa a intenção explícita nos services.
    Optional<ParametrosFinanceirosEntity> findByEmpresaId(Long empresaId);
}
