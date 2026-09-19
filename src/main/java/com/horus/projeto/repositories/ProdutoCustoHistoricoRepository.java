package com.horus.projeto.repositories;

import com.horus.projeto.entities.ProdutoCustoHistoricoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProdutoCustoHistoricoRepository extends JpaRepository<ProdutoCustoHistoricoEntity, Long> {

    /** Trilha de custo de um produto, do mais recente para o mais antigo. */
    List<ProdutoCustoHistoricoEntity> findByEmpresaIdAndCodProdutoOrderByCodHistoricoDesc(
            Long empresaId, Long codProduto);

    long countByEmpresaId(Long empresaId);
}
