-- ============================================================================
-- HÓRUS — CÓDIGO (EAN/SKU) OPCIONAL E ÚNICO POR EMPRESA
-- Idempotente. Corrige dois defeitos herdados do schema original (Josys.sql):
--
--  1. codigo era NOT NULL  -> o cadastro exigia EAN mesmo o frontend e a
--     entidade tratando o campo como opcional.
--  2. UNIQUE (codigo) era GLOBAL -> duas empresas não podiam usar o mesmo EAN.
--     O service valida por empresa (existsByCodigoAndEmpresaId), então o banco
--     recusava o que a regra de negócio permitia, com erro genérico.
--
-- A ORDEM DOS PASSOS IMPORTA: o NOT NULL tem que cair ANTES de qualquer UPDATE
-- que grave NULL na coluna, senão o próprio script esbarra na constraint que
-- veio remover.
-- ============================================================================

-- 1. PRIMEIRO de tudo: tornar o código opcional. ----------------------------
ALTER TABLE produto ALTER COLUMN codigo DROP NOT NULL;

-- 2. Agora é seguro normalizar string vazia para NULL. ----------------------
--    O frontend já envia null, mas dados antigos podem ter '' — e duas strings
--    vazias colidiriam no índice único parcial criado no passo 4.
UPDATE produto SET codigo = NULL WHERE codigo IS NOT NULL AND btrim(codigo) = '';

-- 3. Remover QUALQUER constraint/índice único que esteja só sobre (codigo).
--    O nome é gerado pelo Hibernate e muda por banco, então é localizado por
--    estrutura em vez de por nome, e restrito ao schema corrente.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT con.conname
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
         WHERE rel.relname = 'produto'
           AND rel.relnamespace = current_schema()::regnamespace
           AND con.contype = 'u'
           AND (SELECT array_agg(att.attname::text)
                  FROM unnest(con.conkey) AS k
                  JOIN pg_attribute att
                    ON att.attrelid = con.conrelid AND att.attnum = k) = ARRAY['codigo']
    LOOP
        EXECUTE format('ALTER TABLE produto DROP CONSTRAINT %I', r.conname);
    END LOOP;

    FOR r IN
        SELECT cls.relname AS idxname
          FROM pg_index i
          JOIN pg_class rel ON rel.oid = i.indrelid
          JOIN pg_class cls ON cls.oid = i.indexrelid
         WHERE rel.relname = 'produto'
           AND rel.relnamespace = current_schema()::regnamespace
           AND i.indisunique
           AND NOT i.indisprimary
           AND (SELECT array_agg(att.attname::text)
                  FROM unnest(i.indkey) AS k
                  JOIN pg_attribute att
                    ON att.attrelid = i.indrelid AND att.attnum = k) = ARRAY['codigo']
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', r.idxname);
    END LOOP;
END $$;

-- 4. Guarda: o índice do passo 5 falha se já houver código repetido DENTRO da
--    mesma empresa. Em vez de um erro críptico do Postgres, aponta o conflito.
DO $$
DECLARE conflitos text;
BEGIN
    SELECT string_agg(format('empresa %s / código %s (%s produtos)', empresa_id, codigo, qtd), '; ')
      INTO conflitos
      FROM (SELECT empresa_id, codigo, count(*) AS qtd
              FROM produto
             WHERE codigo IS NOT NULL
             GROUP BY empresa_id, codigo
            HAVING count(*) > 1) d;

    IF conflitos IS NOT NULL THEN
        RAISE EXCEPTION
            'Existem códigos repetidos na mesma empresa; corrija antes de rodar esta migração: %',
            conflitos;
    END IF;
END $$;

-- 5. Unicidade correta: por empresa, ignorando os produtos sem código. ------
--    Índice PARCIAL — vários produtos sem código convivem sem conflito.
CREATE UNIQUE INDEX IF NOT EXISTS uk_produto_codigo_empresa
    ON produto (empresa_id, codigo)
    WHERE codigo IS NOT NULL;
