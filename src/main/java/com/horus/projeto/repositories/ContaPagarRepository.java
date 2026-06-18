package com.horus.projeto.repositories;

import com.horus.projeto.entities.ContaPagarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContaPagarRepository extends JpaRepository<ContaPagarEntity, Long> {

    List<ContaPagarEntity> findByEmpresaIdOrderByDataRegistroDesc(Long empresaId);

    Optional<ContaPagarEntity> findByCodContaPagarAndEmpresaId(Long codContaPagar, Long empresaId);

    @Query("SELECT c FROM ContaPagarEntity c WHERE c.empresa.id = :empresaId AND " +
           "(LOWER(c.descricao) LIKE LOWER(CONCAT('%',:termo,'%')) OR " +
           " LOWER(c.fornecedor) LIKE LOWER(CONCAT('%',:termo,'%')))")
    List<ContaPagarEntity> buscarPorTermoEEmpresa(@Param("termo") String termo,
                                                   @Param("empresaId") Long empresaId);

}
