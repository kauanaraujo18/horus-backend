package com.horus.projeto.repositories;

import com.horus.projeto.entities.ProdutoVendaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProdutoVendaRepository extends JpaRepository<ProdutoVendaEntity, Long> {
}