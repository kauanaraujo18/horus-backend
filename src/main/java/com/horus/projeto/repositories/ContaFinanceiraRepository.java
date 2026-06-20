package com.horus.projeto.repositories;

import com.horus.projeto.entities.ContaFinanceiraEntity;
import com.horus.projeto.enums.TipoConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContaFinanceiraRepository extends JpaRepository<ContaFinanceiraEntity, Long> {

    List<ContaFinanceiraEntity> findByEmpresaIdOrderByNomeAsc(Long empresaId);

    Optional<ContaFinanceiraEntity> findByCodContaAndEmpresaId(Long codConta, Long empresaId);

    long countByEmpresaId(Long empresaId);

    long countByEmpresaIdAndTipoConta(Long empresaId, TipoConta tipoConta);

    /** Soma dos saldos de abertura de todas as contas da empresa. */
    @Query("SELECT COALESCE(SUM(c.saldoInicial), 0) FROM ContaFinanceiraEntity c WHERE c.empresa.id = :empresaId")
    BigDecimal somarSaldoInicial(@Param("empresaId") Long empresaId);
}
