package com.horus.projeto.repositories;

import com.horus.projeto.entities.VendaEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<VendaEntity, Long> {
    // Aqui poderemos adicionar buscas futuras, ex: buscar por data
    List<VendaEntity> findByEmpresaId(Long empresaId, Sort sort);

    Optional<VendaEntity> findByCodVendaAndEmpresaId(Long codVenda, Long empresaId);

    /**
     * Resumo de faturamento do período (vendas não estornadas) — topo da DRE.
     * Retorna uma única linha [valorTotal, desconto, acrescimo, quantidadeVendas].
     * Receita Bruta = valorTotal − acrescimo + desconto (valorTotal já vem líquido).
     */
    @Query("""
           SELECT COALESCE(SUM(v.valorTotal), 0), COALESCE(SUM(v.desconto), 0),
                  COALESCE(SUM(v.acrescimo), 0), COUNT(v)
           FROM VendaEntity v
           WHERE v.empresa.id = :empresaId
             AND v.estornada = false
             AND v.dataVenda BETWEEN :inicio AND :fim
           """)
    List<Object[]> resumoNoPeriodo(@Param("empresaId") Long empresaId,
                                   @Param("inicio") LocalDateTime inicio,
                                   @Param("fim") LocalDateTime fim);

    /**
     * Vendas do período que ainda não têm CMV apurado — sinaliza na DRE que o número
     * está incompleto e que falta rodar o reprocessamento de custeio.
     */
    @Query("""
           SELECT COUNT(v) FROM VendaEntity v
           WHERE v.empresa.id = :empresaId
             AND v.estornada = false
             AND v.dataVenda BETWEEN :inicio AND :fim
             AND NOT EXISTS (SELECT 1 FROM CustoVendaEntity c
                             WHERE c.codVenda = v.codVenda AND c.estornado = false)
           """)
    long contarSemCmvNoPeriodo(@Param("empresaId") Long empresaId,
                               @Param("inicio") LocalDateTime inicio,
                               @Param("fim") LocalDateTime fim);
}
