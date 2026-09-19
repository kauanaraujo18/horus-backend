-- ============================================================================
-- HÓRUS — CMV / DRE GERENCIAL (v1)
-- Razão de COMPETÊNCIA do custo da mercadoria vendida.
-- Rodar uma vez no banco antes do deploy. Idempotente.
--
-- POR QUE UMA TABELA NOVA E NÃO lancamento_financeiro:
-- o razão financeiro é regime de CAIXA e governa o saldo das contas. A saída de
-- dinheiro da mercadoria já é registrada na baixa da parcela do Contas a Pagar
-- ("Compras de Mercadorias"). Lançar o CMV lá dentro contaria a mesma saída duas
-- vezes e o Saldo Final do DFC deixaria de bater com o caixa real.
-- ============================================================================

-- 1. RAZÃO DE CUSTO DA MERCADORIA VENDIDA -----------------------------------
CREATE TABLE IF NOT EXISTS custo_venda (
    cod_custo_venda  BIGSERIAL PRIMARY KEY,
    empresa_id       BIGINT        NOT NULL REFERENCES empresa(id),
    cod_venda        BIGINT        NOT NULL REFERENCES venda(cod_venda),
    cod_item_venda   BIGINT,                          -- nulo em vendas antigas reprocessadas
    cod_produto      BIGINT        NOT NULL,
    nome_produto     VARCHAR(255),                    -- snapshot: sobrevive a renomeação
    cod_classe       BIGINT REFERENCES classe_financeira(cod_classe),
    quantidade       NUMERIC(15,4) NOT NULL,
    custo_unitario   NUMERIC(15,4) NOT NULL,          -- congelado no ato da venda
    custo_total      NUMERIC(15,2) NOT NULL,
    data_movimento   DATE          NOT NULL,          -- COMPETÊNCIA = data da venda
    origem_custo     VARCHAR(12)   NOT NULL,          -- CUSTO_MEDIO | CADASTRO | COMPOSICAO | SEM_CUSTO
    estornado        BOOLEAN       NOT NULL DEFAULT FALSE,
    data_registro    TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_custo_origem CHECK (origem_custo IN ('CUSTO_MEDIO','CADASTRO','COMPOSICAO','SEM_CUSTO')),
    CONSTRAINT chk_custo_valor  CHECK (custo_total >= 0 AND custo_unitario >= 0),
    CONSTRAINT chk_custo_qtd    CHECK (quantidade > 0)
);

-- Índice que serve diretamente o SUM por período da DRE
CREATE INDEX IF NOT EXISTS idx_custo_venda_dre
    ON custo_venda(empresa_id, data_movimento, cod_produto);

-- Índice do estorno em cascata e da guarda de idempotência
CREATE INDEX IF NOT EXISTS idx_custo_venda_venda
    ON custo_venda(cod_venda);

-- 2. PARÂMETROS: classe analítica de CUSTO que recebe o CMV ------------------
-- Quando nula, o backend resolve por convenção (código 4.1.02 do plano padrão).
ALTER TABLE parametros_financeiros
    ADD COLUMN IF NOT EXISTS cod_classe_cmv BIGINT REFERENCES classe_financeira(cod_classe);

-- ============================================================================
-- PÓS-DEPLOY (opcional, pela interface): Financeiro > DRE > "Reprocessar CMV"
-- apura o custo das vendas já existentes. Usa o custo ATUAL dos produtos, pois
-- não há histórico de custo — vendas novas congelam o custo correto no ato.
-- ============================================================================
