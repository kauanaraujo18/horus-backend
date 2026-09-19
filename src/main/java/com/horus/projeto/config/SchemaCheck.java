package com.horus.projeto.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verificador de schema.
 *
 * As migrações deste projeto são arquivos .sql aplicados à mão, sem controle de
 * versão no banco. Quando uma delas é esquecida em um ambiente, o sintoma chega
 * ao usuário como erro genérico de tela ("Verifique os dados") — foi o que
 * aconteceu com produto.codigo NOT NULL e com venda.estornada.
 *
 * Este componente transforma esse silêncio em um aviso explícito no boot,
 * nomeando o arquivo exato que falta rodar. Não altera nada no banco:
 * apenas olha e avisa. A correção continua sendo uma decisão humana.
 */
@Component
@RequiredArgsConstructor
public class SchemaCheck {

    private static final Logger log = LoggerFactory.getLogger(SchemaCheck.class);

    private final JdbcTemplate jdbc;

    /** Cada item: descrição do problema -> migração que o resolve. */
    private record Pendencia(String problema, String migracao) {}

    @PostConstruct
    public void verificar() {
        try {
            List<Pendencia> pendencias = coletarPendencias();
            if (pendencias.isEmpty()) {
                log.info("Schema conferido: todas as migrações conhecidas estão aplicadas.");
                return;
            }
            StringBuilder sb = new StringBuilder("\n")
                    .append("========================================================================\n")
                    .append(" MIGRAÇÕES PENDENTES NESTE BANCO — funcionalidades vão falhar em tela\n")
                    .append("========================================================================\n");
            for (Pendencia p : pendencias) {
                sb.append("  [!] ").append(p.problema()).append('\n')
                  .append("      corrigir com: src/main/resources/db/").append(p.migracao()).append('\n');
            }
            sb.append("------------------------------------------------------------------------\n")
              .append("  psql -U <user> -d <base> -f src/main/resources/db/<arquivo>.sql\n")
              .append("========================================================================");
            log.warn(sb.toString());
        } catch (Exception e) {
            // Nunca derrubar a aplicação por causa do diagnóstico.
            log.warn("Não foi possível verificar o schema: {}", e.getMessage());
        }
    }

    /** Mesmo diagnóstico do boot, em forma de dados — serve o endpoint /api/health/schema. */
    public Map<String, Object> diagnostico() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        try {
            List<Pendencia> pendencias = coletarPendencias();
            resposta.put("ok", pendencias.isEmpty());
            List<Map<String, String>> lista = new ArrayList<>();
            for (Pendencia p : pendencias)
                lista.add(Map.of("problema", p.problema(), "migracao", p.migracao()));
            resposta.put("pendencias", lista);
        } catch (Exception e) {
            resposta.put("ok", false);
            resposta.put("erro", e.getMessage());
        }
        return resposta;
    }

    private List<Pendencia> coletarPendencias() {
        List<Pendencia> p = new ArrayList<>();

        if (!colunaExiste("venda", "estornada"))
            p.add(new Pendencia("venda.estornada não existe — estorno de venda quebra",
                    "estorno_venda_v1.sql"));

        if (colunaObrigatoria("produto", "codigo"))
            p.add(new Pendencia("produto.codigo é NOT NULL — não é possível cadastrar produto sem EAN",
                    "produto_codigo_v1.sql"));

        if (uniqueGlobalEmProdutoCodigo())
            p.add(new Pendencia("UNIQUE global em produto(codigo) — duas empresas não podem usar o mesmo EAN",
                    "produto_codigo_v1.sql"));

        if (!tabelaExiste("custo_venda"))
            p.add(new Pendencia("tabela custo_venda não existe — CMV e DRE não funcionam",
                    "cmv_v1.sql"));

        if (!colunaExiste("parametros_financeiros", "cod_classe_cmv"))
            p.add(new Pendencia("parametros_financeiros.cod_classe_cmv não existe",
                    "cmv_v1.sql"));

        if (!colunaExiste("produto", "custo_medio"))
            p.add(new Pendencia("produto.custo_medio não existe — custo médio ponderado não funciona",
                    "custo_medio_v1.sql"));

        if (!tabelaExiste("produto_custo_historico"))
            p.add(new Pendencia("tabela produto_custo_historico não existe — sem auditoria de custo",
                    "custo_medio_v1.sql"));

        if (!colunaExiste("producao_item", "custo_unitario"))
            p.add(new Pendencia("producao_item.custo_unitario não existe — estorno de produção distorce o custo",
                    "custo_medio_v1.sql"));

        return p;
    }

    // ── Consultas ao catálogo ────────────────────────────────────────────────

    private boolean tabelaExiste(String tabela) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                Integer.class, tabela);
        return n != null && n > 0;
    }

    private boolean colunaExiste(String tabela, String coluna) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema='public' AND table_name=? AND column_name=?",
                Integer.class, tabela, coluna);
        return n != null && n > 0;
    }

    /** true quando a coluna existe E é NOT NULL. */
    private boolean colunaObrigatoria(String tabela, String coluna) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema='public' AND table_name=? AND column_name=? AND is_nullable='NO'",
                Integer.class, tabela, coluna);
        return n != null && n > 0;
    }

    /** Unicidade sobre (codigo) sozinho — deve ser (empresa_id, codigo). */
    private boolean uniqueGlobalEmProdutoCodigo() {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i
                JOIN pg_class rel ON rel.oid = i.indrelid
                WHERE rel.relname = 'produto'
                  AND i.indisunique
                  AND NOT i.indisprimary
                  AND (SELECT array_agg(att.attname::text)
                         FROM unnest(i.indkey) AS k
                         JOIN pg_attribute att ON att.attrelid = i.indrelid AND att.attnum = k)
                      = ARRAY['codigo']
                """, Integer.class);
        return n != null && n > 0;
    }
}
