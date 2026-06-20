package com.horus.projeto.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuração financeira da empresa — 1 linha por empresa (PK = empresa_id).
 * Guarda as contas padrão; a unicidade "1 Caixa e 1 Banco padrão" é estrutural
 * (cada padrão é uma única coluna, impossível ter dois).
 */
@Entity
@Table(name = "parametros_financeiros")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParametrosFinanceirosEntity {

    @Id
    @Column(name = "empresa_id")
    private Long empresaId;

    @Column(name = "cod_conta_caixa_padrao")
    private Long codContaCaixaPadrao;

    @Column(name = "cod_conta_banco_padrao")
    private Long codContaBancoPadrao;
}
