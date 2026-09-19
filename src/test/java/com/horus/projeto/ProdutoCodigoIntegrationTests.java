package com.horus.projeto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.horus.projeto.config.SchemaCheck;
import com.horus.projeto.controllers.ProdutoController;
import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.enums.TipoProduto;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.ProdutoRepository;
import com.horus.projeto.repositories.UsuarioRepository;
import com.horus.projeto.services.ProdutoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressão do cadastro de produto sem código (EAN/SKU).
 *
 * O código é OPCIONAL por regra de negócio, e a unicidade é POR EMPRESA.
 * O schema original (Josys.sql) tinha codigo NOT NULL e UNIQUE global; estes
 * testes garantem que nenhum ambiente volte a esse estado sem quebrar o build.
 *
 * Todos @Transactional: revertem ao final, não sujam a base.
 */
@SpringBootTest
class ProdutoCodigoIntegrationTests {

    @Autowired private ProdutoController produtoController;
    @Autowired private ProdutoService produtoService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SchemaCheck schemaCheck;
    @Autowired private EntityManager em;

    /** JSON idêntico ao que handleSalvarProduto monta com o campo EAN em branco. */
    private static final String JSON_SEM_EAN = """
            {"codigo":null,"nome":"ZZ-TESTE-SEM-EAN","valor":10.0,"valorCusto":null,
             "quantidadeEstoque":5,"tipo":"R","unidadeMedida":null,"referencia":null,
             "codClassePadrao":null}
            """;

    private boolean autenticar() {
        UsuarioEntity u = usuarioRepository.findAll().stream()
                .filter(x -> x.getEmpresa() != null).findFirst().orElse(null);
        if (u == null) return false;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        return true;
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private ProdutoEntity novo(String codigo, String nome, Long empresaId) {
        ProdutoEntity p = new ProdutoEntity();
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setValor(new BigDecimal("10.00"));
        p.setQuantidadeEstoque(5);
        p.setTipo(TipoProduto.R);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Caminho real: JSON -> Jackson -> Controller -> Service -> banco
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void cadastraProdutoSemEanPeloCaminhoCompleto() throws Exception {
        if (!autenticar()) return;

        ProdutoEntity corpo = objectMapper.readValue(JSON_SEM_EAN, ProdutoEntity.class);
        assertNull(corpo.getCodigo(), "o formulário envia null quando o EAN fica em branco");

        ResponseEntity<?> resp = produtoController.salvar(corpo);
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "cadastrar produto sem EAN tem que funcionar — o campo é opcional");

        em.flush(); // força o INSERT: é aqui que uma coluna NOT NULL estouraria

        ProdutoEntity salvo = (ProdutoEntity) resp.getBody();
        assertNotNull(salvo);
        assertNotNull(salvo.getCodProduto());
        assertNull(salvo.getCodigo());
    }

    @Test
    @Transactional
    void varariosProdutosSemEanConvivem() {
        List<EmpresaEntity> empresas = empresaRepository.findAll();
        if (empresas.isEmpty()) return;
        Long empresaId = empresas.get(0).getId();

        produtoService.salvar(novo(null, "ZZ-SEM-EAN-1", empresaId), empresaId);
        produtoService.salvar(novo(null, "ZZ-SEM-EAN-2", empresaId), empresaId);
        produtoService.salvar(novo(null, "ZZ-SEM-EAN-3", empresaId), empresaId);
        em.flush();
        // Passou: o índice único é PARCIAL (ignora NULL). Um índice comum quebraria aqui.
    }

    @Test
    @Transactional
    void stringVaziaEhNormalizadaParaNull() {
        List<EmpresaEntity> empresas = empresaRepository.findAll();
        if (empresas.isEmpty()) return;
        Long empresaId = empresas.get(0).getId();

        ProdutoEntity a = produtoService.salvar(novo("", "ZZ-VAZIO-1", empresaId), empresaId);
        ProdutoEntity b = produtoService.salvar(novo("   ", "ZZ-VAZIO-2", empresaId), empresaId);
        em.flush();

        assertNull(a.getCodigo(), "string vazia vira null — duas strings vazias colidiriam no índice");
        assertNull(b.getCodigo(), "string só com espaços também vira null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. Unicidade é POR EMPRESA, não global
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void mesmoEanPodeExistirEmEmpresasDiferentes() {
        List<EmpresaEntity> empresas = empresaRepository.findAll();
        if (empresas.size() < 2) return; // precisa de duas empresas para o cenário

        Long empresaA = empresas.get(0).getId();
        Long empresaB = empresas.get(1).getId();
        String ean = "7899000000987";

        produtoService.salvar(novo(ean, "ZZ-EAN-EMPRESA-A", empresaA), empresaA);
        produtoService.salvar(novo(ean, "ZZ-EAN-EMPRESA-B", empresaB), empresaB);
        em.flush();
        // Passou: o índice é (empresa_id, codigo). Um UNIQUE global quebraria aqui.
    }

    @Test
    @Transactional
    void mesmoEanNaMesmaEmpresaEhRecusado() {
        List<EmpresaEntity> empresas = empresaRepository.findAll();
        if (empresas.isEmpty()) return;
        Long empresaId = empresas.get(0).getId();
        String ean = "7899000000994";

        produtoService.salvar(novo(ean, "ZZ-EAN-DUP-1", empresaId), empresaId);
        em.flush();

        assertThrows(IllegalArgumentException.class,
                () -> produtoService.salvar(novo(ean, "ZZ-EAN-DUP-2", empresaId), empresaId),
                "dentro da mesma empresa o código continua tendo que ser único");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. O verificador de schema
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void schemaDesteBancoEstaCompleto() {
        Map<String, Object> diag = schemaCheck.diagnostico();
        assertEquals(Boolean.TRUE, diag.get("ok"),
                "há migração pendente neste banco: " + diag.get("pendencias"));
    }
}
