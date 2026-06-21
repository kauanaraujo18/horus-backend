package com.horus.projeto.repositories;

import com.horus.projeto.entities.VendaEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<VendaEntity, Long> {
    // Aqui poderemos adicionar buscas futuras, ex: buscar por data
    List<VendaEntity> findByEmpresaId(Long empresaId, Sort sort);

    Optional<VendaEntity> findByCodVendaAndEmpresaId(Long codVenda, Long empresaId);
}