-- ============================================================================
-- HÓRUS — Conciliação bancária (marcação manual). Idempotente.
--
-- Feito em 4 passos (em vez de ADD COLUMN ... NOT NULL direto) porque o
-- Postgres recusa adicionar coluna NOT NULL sem DEFAULT em tabela que já tem
-- linhas: 'column "conciliado" ... contains null values'.
-- Também conserta bancos onde a coluna já foi criada como nullable.
-- ============================================================================

-- ---------- lancamento_financeiro ----------
ALTER TABLE lancamento_financeiro
    ADD COLUMN IF NOT EXISTS conciliado       BOOLEAN,
    ADD COLUMN IF NOT EXISTS data_conciliacao DATE;

UPDATE lancamento_financeiro SET conciliado = FALSE WHERE conciliado IS NULL;

ALTER TABLE lancamento_financeiro
    ALTER COLUMN conciliado SET DEFAULT FALSE,
    ALTER COLUMN conciliado SET NOT NULL;

-- ---------- transferencia ----------
ALTER TABLE transferencia
    ADD COLUMN IF NOT EXISTS conciliado       BOOLEAN,
    ADD COLUMN IF NOT EXISTS data_conciliacao DATE;

UPDATE transferencia SET conciliado = FALSE WHERE conciliado IS NULL;

ALTER TABLE transferencia
    ALTER COLUMN conciliado SET DEFAULT FALSE,
    ALTER COLUMN conciliado SET NOT NULL;
