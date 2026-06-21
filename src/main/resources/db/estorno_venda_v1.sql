-- ============================================================================
-- HÓRUS — Estorno de Venda
-- Coluna de marcação de venda estornada (mantém o histórico). Idempotente.
-- ============================================================================
ALTER TABLE venda
    ADD COLUMN IF NOT EXISTS estornada BOOLEAN NOT NULL DEFAULT FALSE;
