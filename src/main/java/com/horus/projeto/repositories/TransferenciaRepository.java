package com.horus.projeto.repositories;

import com.horus.projeto.entities.TransferenciaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferenciaRepository extends JpaRepository<TransferenciaEntity, Long> {

    List<TransferenciaEntity> findByEmpresaIdOrderByDataDescCodTransferenciaDesc(Long empresaId);

    List<TransferenciaEntity> findByEmpresaIdAndEstornadoFalse(Long empresaId);

    Optional<TransferenciaEntity> findByCodTransferenciaAndEmpresaId(Long codTransferencia, Long empresaId);

    /** Guarda de exclusão de conta: existe transferência (origem ou destino) usando esta conta? */
    boolean existsByCodContaOrigemOrCodContaDestino(Long codContaOrigem, Long codContaDestino);
}
