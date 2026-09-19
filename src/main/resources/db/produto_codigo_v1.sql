-- ============================================================================
-- HÓRUS — CÓDIGO (EAN/SKU) OPCIONAL E ÚNICO POR EMPRESA
-- Idempotente. Corrige dois defeitos herdados do schema original (Josys.sql):
--
--  1. codigo era NOT NULL  -> o cadastro exigia EAN mesmo o frontend e a
--     entidade tratando o campo como opcional.
--  2. UNIQUE (codigo) era GLOBAL -> duas empresas não podiam usar o mesmo EAN.
--     O service valida por empresa (existsByCodigoAndEmpresaId), então o banco
--     recusava o que a regra de negócio permitia, com erro genérico.
-- ============================================================================

-- 1. Normaliza string vazia para NULL (o frontend já envia null, mas dados
--    antigos podem ter '' — e '' colidiria no índice único).
UPDATE produto SET codigo = NULL WHERE codigo IS NOT NULL AND btrim(codigo) = '';

-- 2. Torna o código opcional.
ALTER TABLE produto ALTER COLUMN codigo DROP NOT NULL;

-- 3. Remove QUALQUER constraint/índice único que esteja só sobre (codigo).
--    O nome é gerado pelo Hibernate e muda por banco, então é localizado por
--    estrutura em vez de por nome.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT con.conname
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
         WHERE rel.relname = 'produto'
           AND con.contype = 'u'
           AND (SELECT array_agg(att.attname::text)
                  FROM unnest(con.conkey) AS k
                  JOIN pg_attribute att
                    ON att.attrelid = con.conrelid AND att.attnum = k) = ARRAY['codigo']
    LOOP
        EXECUTE format('ALTER TABLE produto DROP CONSTRAINT %I', r.conname);
    END LOOP;

    FOR r IN
        SELECT i.indexrelid::regclass::text AS idxname
          FROM pg_index i
          JOIN pg_class rel ON rel.oid = i.indrelid
         WHERE rel.relname = 'produto'
           AND i.indisunique
           AND NOT i.indisprimary
           AND (SELECT array_agg(att.attname::text)
                  FROM unnest(i.indkey) AS k
                  JOIN pg_attribute att
                    ON att.attrelid = i.indrelid AND att.attnum = k) = ARRAY['codigo']
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %s', r.idxname);
    END LOOP;
END $$;

-- 4. Unicidade correta: por empresa, ignorando os produtos sem código.
--    Índice PARCIAL — vários produtos sem código convivem sem conflito.
CREATE UNIQUE INDEX IF NOT EXISTS uk_produto_codigo_empresa
    ON produto (empresa_id, codigo)
    WHERE codigo IS NOT NULL;
