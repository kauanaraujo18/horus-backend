-- ============================================================================
-- HÓRUS — ESTOQUE E QUANTIDADES EM DECIMAL (v1)
-- Idempotente. Depende de custo_medio_v1.sql.
--
-- POR QUÊ:
-- quantidade_estoque era INTEGER. Insumo se consome fracionado (0,350 kg de
-- farinha, 1,5 L de leite), e a produção truncava esse consumo com .intValue():
-- consumir 0,350 kg dava baixa de ZERO no estoque. O saldo derivava do real a
-- cada produção, e o custo médio ponderado herdava esse erro.
--
-- ESCALA: quantidades em NUMERIC(15,3) — 3 casas cobrem grama, mililitro e
-- milímetro. Campos de CUSTO seguem em NUMERIC(15,4): são valor por unidade,
-- onde a casa extra evita acúmulo de arredondamento na explosão da composição.
--
-- SEGURO: alargar INTEGER -> NUMERIC preserva todos os valores existentes
-- (10 vira 10,000). Nenhum dado é perdido e a operação não precisa de janela.
-- ============================================================================

-- 1. SALDO EM ESTOQUE -------------------------------------------------------
ALTER TABLE produto
    ALTER COLUMN quantidade_estoque TYPE NUMERIC(15,3)
    USING COALESCE(quantidade_estoque, 0)::NUMERIC(15,3);

ALTER TABLE produto ALTER COLUMN quantidade_estoque SET DEFAULT 0;

-- 2. QUANTIDADES DO CICLO (venda, compra, produção) -------------------------
ALTER TABLE produto_venda
    ALTER COLUMN quantidade TYPE NUMERIC(15,3)
    USING COALESCE(quantidade, 0)::NUMERIC(15,3);

ALTER TABLE conta_pagar_item
    ALTER COLUMN quantidade TYPE NUMERIC(15,3)
    USING COALESCE(quantidade, 0)::NUMERIC(15,3);

ALTER TABLE producao
    ALTER COLUMN quantidade_produzida TYPE NUMERIC(15,3)
    USING COALESCE(quantidade_produzida, 0)::NUMERIC(15,3);

-- 3. QUANTIDADES JÁ DECIMAIS — apenas fixa a escala em 3 --------------------
ALTER TABLE producao_item
    ALTER COLUMN quantidade_consumida TYPE NUMERIC(15,3)
    USING COALESCE(quantidade_consumida, 0)::NUMERIC(15,3);

-- Quantidade da receita (BOM): 0,350 kg de farinha por unidade produzida.
ALTER TABLE materia_prima
    ALTER COLUMN quantidade TYPE NUMERIC(15,3)
    USING COALESCE(quantidade, 0)::NUMERIC(15,3);

ALTER TABLE custo_venda
    ALTER COLUMN quantidade TYPE NUMERIC(15,3)
    USING COALESCE(quantidade, 0)::NUMERIC(15,3);

ALTER TABLE produto_custo_historico
    ALTER COLUMN quantidade_anterior TYPE NUMERIC(15,3)
    USING COALESCE(quantidade_anterior, 0)::NUMERIC(15,3);
ALTER TABLE produto_custo_historico
    ALTER COLUMN quantidade_entrada  TYPE NUMERIC(15,3)
    USING COALESCE(quantidade_entrada, 0)::NUMERIC(15,3);
ALTER TABLE produto_custo_historico
    ALTER COLUMN quantidade_nova     TYPE NUMERIC(15,3)
    USING COALESCE(quantidade_nova, 0)::NUMERIC(15,3);

-- 4. GUARDA: estoque não pode ficar negativo --------------------------------
-- Toda saída no código já valida saldo antes de debitar; isto é a rede de
-- proteção no banco, para que nenhum caminho futuro fure a regra em silêncio.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conname = 'chk_produto_estoque_nao_negativo') THEN
        ALTER TABLE produto
            ADD CONSTRAINT chk_produto_estoque_nao_negativo
            CHECK (quantidade_estoque >= 0);
    END IF;
END $$;
