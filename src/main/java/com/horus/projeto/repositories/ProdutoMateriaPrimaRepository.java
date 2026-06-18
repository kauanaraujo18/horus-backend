package com.horus.projeto.repositories;

import com.horus.projeto.entities.MateriaPrimaId;
import com.horus.projeto.entities.ProdutoMateriaPrimaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProdutoMateriaPrimaRepository extends JpaRepository<ProdutoMateriaPrimaEntity, MateriaPrimaId> {

    List<ProdutoMateriaPrimaEntity> findByIdCodProdutoFinal(Long codProdutoFinal);

    @Query("SELECT p FROM ProdutoMateriaPrimaEntity p WHERE p.produtoFinal.empresa.id = :empresaId")
    List<ProdutoMateriaPrimaEntity> findAllByEmpresaId(@Param("empresaId") Long empresaId);
}
