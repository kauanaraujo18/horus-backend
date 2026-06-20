-- ============================================================================
-- HÓRUS — MÓDULO GESTOR FINANCEIRO (v1)
-- Plano de Contas (classe_financeira) + Razão (lancamento_financeiro)
-- Regime de CAIXA. Rodar uma vez no banco de produção (Supabase) antes do deploy.
-- Idempotente: usa IF NOT EXISTS onde o PostgreSQL permite.
-- ============================================================================

-- 1. PLANO DE CONTAS (árvore: sintética agrupa, analítica recebe lançamento) --
CREATE TABLE IF NOT EXISTS classe_financeira (
    cod_classe       BIGSERIAL PRIMARY KEY,
    empresa_id       BIGINT      NOT NULL REFERENCES empresa(id),
    nome             VARCHAR(150) NOT NULL,
    tipo             VARCHAR(10)  NOT NULL,          -- RECEITA | CUSTO | DESPESA
    nivel            VARCHAR(10)  NOT NULL,          -- SINTETICA | ANALITICA
    cod_classe_pai   BIGINT REFERENCES classe_financeira(cod_classe),
    codigo           VARCHAR(20),                    -- código hierárquico, ex: "3.1.01"
    ativo            BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_classe_tipo  CHECK (tipo  IN ('RECEITA','CUSTO','DESPESA')),
    CONSTRAINT chk_classe_nivel CHECK (nivel IN ('SINTETICA','ANALITICA'))
);
CREATE INDEX IF NOT EXISTS idx_classe_empresa ON classe_financeira(empresa_id);
CREATE INDEX IF NOT EXISTS idx_classe_pai     ON classe_financeira(cod_classe_pai);

-- 2. RAZÃO / LIVRO DE LANÇAMENTOS (append-only; nunca deletar, sempre estornar) --
CREATE TABLE IF NOT EXISTS lancamento_financeiro (
    cod_lancamento   BIGSERIAL PRIMARY KEY,
    empresa_id       BIGINT       NOT NULL REFERENCES empresa(id),
    cod_classe       BIGINT       NOT NULL REFERENCES classe_financeira(cod_classe),
    tipo_movimento   VARCHAR(8)   NOT NULL,          -- ENTRADA | SAIDA
    valor            NUMERIC(15,2) NOT NULL,
    data_movimento   DATE         NOT NULL,          -- data do CAIXA (recebido/pago) — eixo do DFC
    descricao        VARCHAR(200),
    origem           VARCHAR(15)  NOT NULL,          -- VENDA | CONTA_PAGAR | MANUAL | ESTORNO
    origem_id        BIGINT,                         -- rastreabilidade (cod_venda / cod_parcela / ...)
    estornado        BOOLEAN      NOT NULL DEFAULT FALSE,
    cod_estorno_de   BIGINT REFERENCES lancamento_financeiro(cod_lancamento),
    data_registro    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_lanc_mov   CHECK (tipo_movimento IN ('ENTRADA','SAIDA')),
    CONSTRAINT chk_lanc_valor CHECK (valor >= 0)
);
-- Índice que serve diretamente o GROUP BY do DFC
CREATE INDEX IF NOT EXISTS idx_lanc_dfc
    ON lancamento_financeiro(empresa_id, data_movimento, cod_classe);

-- 3. ALTERAÇÕES NAS TABELAS EXISTENTES ---------------------------------------
-- Produto: classe analítica de RECEITA padrão (usada na automação da venda)
ALTER TABLE produto
    ADD COLUMN IF NOT EXISTS cod_classe_padrao BIGINT REFERENCES classe_financeira(cod_classe);

-- Conta a Pagar: classe analítica de CUSTO/DESPESA do título
ALTER TABLE conta_pagar
    ADD COLUMN IF NOT EXISTS cod_classe BIGINT REFERENCES classe_financeira(cod_classe);

-- Parcela: data real do pagamento (regime de caixa data a saída pelo dia que pagou)
ALTER TABLE conta_pagar_parcela
    ADD COLUMN IF NOT EXISTS data_pagamento DATE;
