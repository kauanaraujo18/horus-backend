-- ============================================================================
-- HÓRUS — Transferência de saldo entre contas (neutra ao DFC). Idempotente.
-- ============================================================================
CREATE TABLE IF NOT EXISTS transferencia (
    cod_transferencia BIGSERIAL PRIMARY KEY,
    empresa_id        BIGINT       NOT NULL REFERENCES empresa(id),
    cod_conta_origem  BIGINT       NOT NULL REFERENCES conta_financeira(cod_conta),
    cod_conta_destino BIGINT       NOT NULL REFERENCES conta_financeira(cod_conta),
    valor             NUMERIC(15,2) NOT NULL,
    data              DATE         NOT NULL,
    descricao         VARCHAR(200),
    estornado         BOOLEAN      NOT NULL DEFAULT FALSE,
    data_registro     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_transf_contas CHECK (cod_conta_origem <> cod_conta_destino),
    CONSTRAINT chk_transf_valor  CHECK (valor > 0)
);
CREATE INDEX IF NOT EXISTS idx_transf_empresa ON transferencia(empresa_id);
