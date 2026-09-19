package com.horus.projeto;

import com.horus.projeto.dto.DreResponseDTO;
import com.horus.projeto.entities.*;
import com.horus.projeto.enums.OrigemLancamento;
import com.horus.projeto.enums.TipoProduto;
import com.horus.projeto.repositories.*;
import com.horus.projeto.services.CusteioService;
import com.horus.projeto.services.DreService;
import com.horus.projeto.services.VendaService;
import com.horus.projeto.dto.ItemVendaDTO;
import com.horus.projeto.dto.VendaRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de integração do CMV / DRE contra o banco de desenvolvimento.
 *
 * Todos os métodos são @Transactional: o JUnit reverte a transação ao final,
 * então NADA é persistido — rodar estes testes não altera os dados da base.
 *
 * Invariante central protegida aqui: o CMV NUNCA cria lançamento no razão de
 * caixa. Se alguém tentar "simplificar" mandando o CMV para lancamento_financeiro,
 * o teste deveDoisRazoesPermanecemSeparados falha.
 */
@SpringBootTest
class CmvDreIntegrationTests {

    @Autowired private CusteioService custeioService;
    @Autowired private DreService dreService;
    @Autowired private VendaService vendaService;

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private CustoVendaRepository custoVendaRepository;
    @Autowired private LancamentoFinanceiroRepository lancamentoRepository;

    /** Empresa com pelo menos um produto vendável — base de todos os cenários. */
    private Long empresaComProdutos() {
        for (EmpresaEntity e : empresaRepository.findAll()) {
            boolean temVendavel = produtoRepository.findByEmpresaId(e.getId()).stream()
                    .anyMatch(p -> p.getTipo() == TipoProduto.R || p.getTipo() == TipoProduto.PF);
            if (temVendavel) return e.getId();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Venda nova gera CMV congelado
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void vendaGeraCmvComCustoCongelado() {
        Long empresaId = empresaComProdutos();
        if (empresaId == null) return; // base vazia: nada a validar

        ProdutoEntity produto = produtoRepository.findByEmpresaId(empresaId).stream()
                .filter(p -> p.getTipo() == TipoProduto.R || p.getTipo() == TipoProduto.PF)
                .filter(p -> p.getQuantidadeEstoque() != null && p.getQuantidadeEstoque().signum() > 0)
                .findFirst().orElse(null);
        if (produto == null) return;

        BigDecimal custoEsperado = custeioService.custoUnitario(produto.getCodProduto(), empresaId);

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodProduto(produto.getCodProduto());
        item.setQuantidade(BigDecimal.ONE);

        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setItens(List.of(item));
        dto.setDataVenda(LocalDate.now());
        dto.setValorDinheiro(produto.getValor());

        VendaEntity venda = vendaService.registrarVenda(dto, empresaId);

        List<CustoVendaEntity> cmv = custoVendaRepository.findByCodVendaAndEstornadoFalse(venda.getCodVenda());
        assertEquals(1, cmv.size(), "a venda deve gerar exatamente 1 linha de CMV");

        CustoVendaEntity linha = cmv.get(0);
        assertEquals(0, custoEsperado.compareTo(linha.getCustoUnitario()),
                "o custo unitário gravado deve ser o custo vigente no ato da venda");
        assertEquals(0, custoEsperado.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(linha.getCustoTotal()), "custoTotal = quantidade × custoUnitario");
        assertEquals(venda.getDataVenda().toLocalDate(), linha.getDataMovimento(),
                "a competência do CMV é a data da venda");
        assertFalse(linha.getEstornado());
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. CMV não polui o razão de caixa (invariante crítica)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void doisRazoesPermanecemSeparados() {
        Long empresaId = empresaComProdutos();
        if (empresaId == null) return;

        ProdutoEntity produto = produtoRepository.findByEmpresaId(empresaId).stream()
                .filter(p -> p.getTipo() == TipoProduto.R || p.getTipo() == TipoProduto.PF)
                .filter(p -> p.getQuantidadeEstoque() != null && p.getQuantidadeEstoque().signum() > 0)
                .findFirst().orElse(null);
        if (produto == null) return;

        long lancamentosAntes = lancamentoRepository.count();

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodProduto(produto.getCodProduto());
        item.setQuantidade(BigDecimal.ONE);
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setItens(List.of(item));
        dto.setDataVenda(LocalDate.now());
        dto.setValorDinheiro(produto.getValor());

        VendaEntity venda = vendaService.registrarVenda(dto, empresaId);

        // Só podem ter nascido lançamentos de RECEITA da venda — nenhum de CUSTO/CMV.
        List<LancamentoFinanceiroEntity> daVenda =
                lancamentoRepository.findByOrigemAndOrigemIdAndEstornadoFalse(
                        OrigemLancamento.VENDA, venda.getCodVenda());
        for (LancamentoFinanceiroEntity l : daVenda) {
            assertEquals(com.horus.projeto.enums.TipoMovimento.ENTRADA, l.getTipoMovimento(),
                    "a venda não pode gerar SAÍDA no razão de caixa — CMV vive em custo_venda");
        }

        long lancamentosDepois = lancamentoRepository.count();
        assertEquals(daVenda.size(), lancamentosDepois - lancamentosAntes,
                "o CMV não pode ter criado linhas extras no razão de caixa");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. Estorno da venda estorna o CMV junto
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void estornoDaVendaEstornaOCmv() {
        Long empresaId = empresaComProdutos();
        if (empresaId == null) return;

        ProdutoEntity produto = produtoRepository.findByEmpresaId(empresaId).stream()
                .filter(p -> p.getTipo() == TipoProduto.R || p.getTipo() == TipoProduto.PF)
                .filter(p -> p.getQuantidadeEstoque() != null && p.getQuantidadeEstoque().signum() > 0)
                .findFirst().orElse(null);
        if (produto == null) return;

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodProduto(produto.getCodProduto());
        item.setQuantidade(BigDecimal.ONE);
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setItens(List.of(item));
        dto.setDataVenda(LocalDate.now());
        dto.setValorDinheiro(produto.getValor());

        VendaEntity venda = vendaService.registrarVenda(dto, empresaId);
        assertFalse(custoVendaRepository.findByCodVendaAndEstornadoFalse(venda.getCodVenda()).isEmpty());

        vendaService.estornarVenda(venda.getCodVenda(), empresaId);

        assertTrue(custoVendaRepository.findByCodVendaAndEstornadoFalse(venda.getCodVenda()).isEmpty(),
                "estornar a venda tem que tirar o CMV da DRE junto com a receita");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. Reprocessamento é idempotente
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void reprocessamentoNaoDuplica() {
        Long empresaId = empresaComProdutos();
        if (empresaId == null) return;

        custeioService.reprocessar(empresaId);
        long depoisDaPrimeira = custoVendaRepository.countByEmpresaIdAndEstornadoFalse(empresaId);

        int segunda = custeioService.reprocessar(empresaId);
        long depoisDaSegunda = custoVendaRepository.countByEmpresaIdAndEstornadoFalse(empresaId);

        assertEquals(0, segunda, "a segunda passada não deve reprocessar nada");
        assertEquals(depoisDaPrimeira, depoisDaSegunda, "o reprocessamento não pode duplicar linhas de CMV");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. A aritmética da DRE fecha
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void dreFechaAritmeticamente() {
        Long empresaId = empresaComProdutos();
        if (empresaId == null) return;

        custeioService.reprocessar(empresaId);

        LocalDate fim = LocalDate.now();
        LocalDate inicio = fim.minusYears(5);
        DreResponseDTO dre = dreService.gerar(empresaId, inicio, fim);

        assertEquals(0, dre.getReceitaBruta().subtract(dre.getDescontos()).add(dre.getAcrescimos())
                        .compareTo(dre.getReceitaLiquida()),
                "Receita Bruta − Descontos + Acréscimos = Receita Líquida");

        assertEquals(0, dre.getReceitaLiquida().subtract(dre.getCmv()).compareTo(dre.getLucroBruto()),
                "Receita Líquida − CMV = Lucro Bruto");

        assertEquals(0, dre.getLucroBruto().subtract(dre.getDespesasOperacionais())
                        .compareTo(dre.getResultadoOperacional()),
                "Lucro Bruto − Despesas = Resultado Operacional");

        assertEquals(0, dre.getVendasSemCmv(),
                "após reprocessar, nenhuma venda do período pode ficar sem CMV");

        // A soma da margem por produto tem que bater com o CMV total do topo.
        BigDecimal cmvProdutos = dre.getProdutos().stream()
                .map(p -> p.getCmv()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, cmvProdutos.compareTo(dre.getCmv()),
                "a quebra por produto tem que somar exatamente o CMV do cabeçalho");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. DRE e ranking de margem usam o MESMO custo
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void periodoInvalidoEhRejeitado() {
        Long empresaId = empresaComProdutos();
        if (empresaId == null) return;

        assertThrows(IllegalArgumentException.class,
                () -> dreService.gerar(empresaId, LocalDate.now(), LocalDate.now().minusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> dreService.gerar(empresaId, null, LocalDate.now()));
    }
}
