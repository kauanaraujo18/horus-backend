-- ============================================================================
-- HÓRUS — CONTAS FINANCEIRAS + PARÂMETROS (expansão do módulo financeiro)
-- Onde o dinheiro fica (Caixa/Banco) + contas padrão da empresa.
-- Rodar uma vez no banco antes do deploy. Idempotente.
-- ============================================================================

-- 1. Onde o dinheiro fica armazenado --------------------------------------
CREATE TABLE IF NOT EXISTS conta_financeira (
    cod_conta       BIGSERIAL PRIMARY KEY,
    empresa_id      BIGINT       NOT NULL REFERENCES empresa(id),
    nome            VARCHAR(120) NOT NULL,
    tipo_conta      VARCHAR(10)  NOT NULL,            -- CAIXA | BANCO
    saldo_inicial   NUMERIC(15,2) NOT NULL DEFAULT 0,
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_conta_tipo CHECK (tipo_conta IN ('CAIXA','BANCO'))
);
CREATE INDEX IF NOT EXISTS idx_conta_empresa ON conta_financeira(empresa_id);

-- 2. Configuração financeira da empresa (1 linha por empresa) --------------
-- A unicidade "1 padrão Caixa e 1 padrão Banco" é estrutural: 1 coluna = 1 valor.
CREATE TABLE IF NOT EXISTS parametros_financeiros (
    empresa_id              BIGINT PRIMARY KEY REFERENCES empresa(id),
    cod_conta_caixa_padrao  BIGINT REFERENCES conta_financeira(cod_conta),
    cod_conta_banco_padrao  BIGINT REFERENCES conta_financeira(cod_conta)
);

-- 3. Alterações nas tabelas existentes -------------------------------------
-- Conta onde o movimento entrou/saiu (dá os saldos por conta)
ALTER TABLE lancamento_financeiro
    ADD COLUMN IF NOT EXISTS cod_conta_financeira BIGINT REFERENCES conta_financeira(cod_conta);

-- Conta de onde o pagamento da despesa sai (informada na baixa)
ALTER TABLE conta_pagar
    ADD COLUMN IF NOT EXISTS cod_conta_financeira BIGINT REFERENCES conta_financeira(cod_conta);

-- Índice para saldo por conta ao longo do tempo
CREATE INDEX IF NOT EXISTS idx_lanc_conta
    ON lancamento_financeiro(empresa_id, cod_conta_financeira, data_movimento);
