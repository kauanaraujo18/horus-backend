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

    /**
     * Detector de contagem dupla na DRE: compras que ENTRARAM EM ESTOQUE (têm itens)
     * mas foram classificadas numa classe de DESPESA.
     *
     * Mercadoria é estoque, não despesa: ela vira resultado como CMV quando é vendida.
     * Classificada como despesa, o mesmo custo entra duas vezes no resultado — uma na
     * linha de Despesas Operacionais e outra na linha do CMV.
     *
     * Retorna [quantidadeDeContas, valorPagoNoPeriodo].
     */
    @Query("""
           SELECT COUNT(DISTINCT c.codContaPagar), COALESCE(SUM(p.valorParcela), 0)
           FROM ContaPagarEntity c JOIN c.parcelas p
           WHERE c.empresa.id = :empresaId
             AND p.paga = true
             AND p.dataPagamento BETWEEN :inicio AND :fim
             AND c.codClasse IN :classesDespesa
             AND EXISTS (SELECT 1 FROM ContaPagarItemEntity i WHERE i.contaPagar = c)
           """)
    List<Object[]> comprasClassificadasComoDespesa(@Param("empresaId") Long empresaId,
                                                    @Param("inicio") java.time.LocalDate inicio,
                                                    @Param("fim") java.time.LocalDate fim,
                                                    @Param("classesDespesa") List<Long> classesDespesa);
}
