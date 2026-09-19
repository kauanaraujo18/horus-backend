-- ============================================================================
-- HÓRUS — CUSTO MÉDIO PONDERADO MÓVEL (v1)
-- Depende de cmv_v1.sql. Rodar uma vez no banco antes do deploy. Idempotente.
--
-- O QUE MUDA:
-- valor_custo deixa de ser a base de custo e vira apenas o custo INFORMADO.
-- A base autoritativa passa a ser custo_medio, recalculado a cada ENTRADA de
-- estoque (compra, produção, estorno). Saída não altera custo, só quantidade.
-- ============================================================================

-- 1. CUSTO MÉDIO NO PRODUTO --------------------------------------------------
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS custo_medio NUMERIC(15,4);

-- 2. TRILHA DE AUDITORIA DO CUSTO -------------------------------------------
-- Uma linha por entrada, com os dois lados da ponderação. É o que permite
-- responder "por que o custo deste produto é R$ 6,37?" meses depois.
CREATE TABLE IF NOT EXISTS produto_custo_historico (
    cod_historico        BIGSERIAL PRIMARY KEY,
    empresa_id           BIGINT        NOT NULL REFERENCES empresa(id),
    cod_produto          BIGINT        NOT NULL,
    quantidade_anterior  NUMERIC(15,4) NOT NULL,
    custo_anterior       NUMERIC(15,4) NOT NULL,
    quantidade_entrada   NUMERIC(15,4) NOT NULL,
    custo_entrada        NUMERIC(15,4) NOT NULL,
    quantidade_nova      NUMERIC(15,4) NOT NULL,
    custo_novo           NUMERIC(15,4) NOT NULL,
    origem               VARCHAR(20)   NOT NULL,
    origem_id            BIGINT,
    descricao            VARCHAR(200),
    data_movimento       DATE          NOT NULL,
    data_registro        TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT chk_hist_origem CHECK (origem IN
        ('COMPRA','PRODUCAO','ESTORNO_VENDA','ESTORNO_PRODUCAO','AJUSTE_MANUAL','SALDO_INICIAL'))
);

CREATE INDEX IF NOT EXISTS idx_custo_hist_produto
    ON produto_custo_historico(empresa_id, cod_produto, cod_historico DESC);

-- 3. SNAPSHOT DE CUSTO NA PRODUÇÃO -------------------------------------------
-- producao_item.custo_unitario é obrigatório para o estorno devolver o insumo
-- ao estoque pelo custo com que ele saiu (devolver pelo custo de hoje
-- contaminaria o custo médio com um valor que nunca foi pago).
ALTER TABLE producao
    ADD COLUMN IF NOT EXISTS custo_total    NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS custo_unitario NUMERIC(15,4);

ALTER TABLE producao_item
    ADD COLUMN IF NOT EXISTS custo_unitario NUMERIC(15,4);

-- 4. NOVA ORIGEM DE CUSTO NO CMV ---------------------------------------------
-- custo_venda passa a poder registrar que o custo veio do custo médio.
ALTER TABLE custo_venda DROP CONSTRAINT IF EXISTS chk_custo_origem;
ALTER TABLE custo_venda ADD CONSTRAINT chk_custo_origem
    CHECK (origem_custo IN ('CUSTO_MEDIO','CADASTRO','COMPOSICAO','SEM_CUSTO'));

-- 5. SEMEADURA DO CUSTO MÉDIO ------------------------------------------------
-- Produtos SEM composição herdam o custo já cadastrado, para não regredir.
-- Produtos COM composição ficam com custo_medio nulo de propósito: continuam
-- usando a explosão da BOM até a primeira produção real dar-lhes custo próprio.
UPDATE produto p
   SET custo_medio = p.valor_custo
 WHERE p.custo_medio IS NULL
   AND p.valor_custo IS NOT NULL
   AND p.valor_custo > 0
   AND NOT EXISTS (SELECT 1 FROM materia_prima m WHERE m.cod_produto_final = p.cod_produto);

-- Registra a semeadura na trilha de auditoria (só para quem foi semeado agora).
INSERT INTO produto_custo_historico (
    empresa_id, cod_produto, quantidade_anterior, custo_anterior,
    quantidade_entrada, custo_entrada, quantidade_nova, custo_novo,
    origem, descricao, data_movimento)
SELECT p.empresa_id, p.cod_produto,
       COALESCE(p.quantidade_estoque, 0), 0,
       0, p.custo_medio,
       COALESCE(p.quantidade_estoque, 0), p.custo_medio,
       'SALDO_INICIAL', 'Custo cadastrado convertido em custo médio (migração)', CURRENT_DATE
  FROM produto p
 WHERE p.custo_medio IS NOT NULL
   AND p.custo_medio > 0
   AND p.empresa_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM produto_custo_historico h WHERE h.cod_produto = p.cod_produto);

-- ============================================================================
-- PÓS-DEPLOY: rode Financeiro > DRE > "Reprocessar CMV" de novo para que as
-- vendas passem a usar o custo médio semeado acima.
-- ============================================================================
