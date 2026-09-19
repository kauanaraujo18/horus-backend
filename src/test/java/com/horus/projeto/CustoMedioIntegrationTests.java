package com.horus.projeto;

import com.horus.projeto.dto.*;
import com.horus.projeto.entities.*;
import com.horus.projeto.enums.OrigemEntradaEstoque;
import com.horus.projeto.enums.TipoProduto;
import com.horus.projeto.repositories.*;
import com.horus.projeto.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de integração do custo médio ponderado móvel.
 *
 * Todos @Transactional: o JUnit reverte tudo no final — rodar não altera a base.
 *
 * Os produtos usados são CRIADOS pelo próprio teste, para que a aritmética seja
 * verificável a partir de um estado conhecido em vez de depender dos dados que
 * por acaso existam no banco.
 */
@SpringBootTest
class CustoMedioIntegrationTests {

    @Autowired private CusteioService custeioService;
    @Autowired private ContaPagarService contaPagarService;
    @Autowired private ProducaoService producaoService;
    @Autowired private VendaService vendaService;
    @Autowired private ProdutoService produtoService;

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ProdutoMateriaPrimaRepository materiaPrimaRepository;
    @Autowired private ProdutoCustoHistoricoRepository historicoRepository;

    private Long empresaId() {
        List<EmpresaEntity> todas = empresaRepository.findAll();
        return todas.isEmpty() ? null : todas.get(0).getId();
    }

    private ProdutoEntity criarProduto(Long empresaId, String nome, TipoProduto tipo,
                                       BigDecimal venda, BigDecimal custo, String estoque) {
        ProdutoEntity p = new ProdutoEntity();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setValor(venda);
        p.setValorCusto(custo);
        p.setQuantidadeEstoque(new BigDecimal(estoque));
        p.setEmpresa(empresaRepository.getReferenceById(empresaId));
        return produtoRepository.save(p);
    }

    private ContaPagarRequestDTO compra(ProdutoEntity produto, String qtd, BigDecimal valorUnitario) {
        ContaPagarItemDTO item = new ContaPagarItemDTO();
        item.setCodProduto(produto.getCodProduto());
        item.setQuantidade(new BigDecimal(qtd));
        item.setValorUnitario(valorUnitario);

        ContaPagarParcelaDTO parcela = new ContaPagarParcelaDTO();
        parcela.setNumeroParcela(1);
        parcela.setValorParcela(valorUnitario.multiply(new BigDecimal(qtd)));
        parcela.setDataVencimento(LocalDate.now().plusDays(30));

        ContaPagarRequestDTO dto = new ContaPagarRequestDTO();
        dto.setDescricao("Compra de teste");
        dto.setPaga(false);
        dto.setItens(List.of(item));
        dto.setParcelas(List.of(parcela));
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. A aritmética da ponderação
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void compraPonderaOCustoCorretamente() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        // 10 unidades a R$ 5,00 em estoque
        ProdutoEntity p = criarProduto(empresaId, "TESTE-CMP-A", TipoProduto.R,
                new BigDecimal("12.00"), new BigDecimal("5.00"), "10");
        custeioService.definirCustoManual(p, empresaId, new BigDecimal("5.00"));
        produtoRepository.save(p);

        // Compra 10 unidades a R$ 7,00  ->  (10×5 + 10×7) / 20 = 6,00
        contaPagarService.salvar(compra(p, "10", new BigDecimal("7.00")), empresaId);

        ProdutoEntity depois = produtoRepository.findById(p.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("20").compareTo(depois.getQuantidadeEstoque()), "estoque = 10 + 10");
        assertEquals(0, new BigDecimal("6.0000").compareTo(depois.getCustoMedio()),
                "custo médio = (10×5 + 10×7) / 20 = 6,00");
    }

    @Test
    @Transactional
    void ponderacaoRespeitaOPesoDasQuantidades() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        // 90 a R$ 1,00 + 10 a R$ 11,00  ->  (90 + 110) / 100 = 2,00
        ProdutoEntity p = criarProduto(empresaId, "TESTE-CMP-B", TipoProduto.R,
                new BigDecimal("20.00"), new BigDecimal("1.00"), "90");
        custeioService.definirCustoManual(p, empresaId, new BigDecimal("1.00"));
        produtoRepository.save(p);

        contaPagarService.salvar(compra(p, "10", new BigDecimal("11.00")), empresaId);

        ProdutoEntity depois = produtoRepository.findById(p.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("2.0000").compareTo(depois.getCustoMedio()),
                "a compra pequena e cara não pode puxar a média como se fosse metade");
    }

    @Test
    @Transactional
    void semCustoAnteriorAPrimeiraCompraDefineOCusto() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        // Estoque histórico sem custo nenhum: a compra não pode ser diluída pela metade
        ProdutoEntity p = criarProduto(empresaId, "TESTE-CMP-C", TipoProduto.R,
                new BigDecimal("15.00"), null, "10");

        contaPagarService.salvar(compra(p, "10", new BigDecimal("8.00")), empresaId);

        ProdutoEntity depois = produtoRepository.findById(p.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("8.0000").compareTo(depois.getCustoMedio()),
                "sem custo anterior, o custo da entrada vale para todo o estoque (não 4,00)");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. Saída não altera custo médio
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void vendaNaoAlteraOCustoMedio() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity p = criarProduto(empresaId, "TESTE-CMP-D", TipoProduto.R,
                new BigDecimal("20.00"), new BigDecimal("6.00"), "50");
        custeioService.definirCustoManual(p, empresaId, new BigDecimal("6.00"));
        produtoRepository.save(p);

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodProduto(p.getCodProduto());
        item.setQuantidade(new BigDecimal("10"));
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setItens(List.of(item));
        dto.setDataVenda(LocalDate.now());
        dto.setValorDinheiro(new BigDecimal("200.00"));
        vendaService.registrarVenda(dto, empresaId);

        ProdutoEntity depois = produtoRepository.findById(p.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("40").compareTo(depois.getQuantidadeEstoque()), "a venda reduz a quantidade");
        assertEquals(0, new BigDecimal("6.0000").compareTo(depois.getCustoMedio()),
                "saída NÃO pode alterar o custo médio — essa é a definição do método");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. Estorno devolve pelo custo original
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void estornoDeVendaNaoContaminaOCustoMedio() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity p = criarProduto(empresaId, "TESTE-CMP-E", TipoProduto.R,
                new BigDecimal("20.00"), new BigDecimal("6.00"), "50");
        custeioService.definirCustoManual(p, empresaId, new BigDecimal("6.00"));
        produtoRepository.save(p);

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodProduto(p.getCodProduto());
        item.setQuantidade(new BigDecimal("10"));
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setItens(List.of(item));
        dto.setDataVenda(LocalDate.now());
        dto.setValorDinheiro(new BigDecimal("200.00"));
        VendaEntity venda = vendaService.registrarVenda(dto, empresaId);

        // Custo muda DEPOIS da venda — o estorno não pode usar este valor novo
        ProdutoEntity meio = produtoRepository.findById(p.getCodProduto()).orElseThrow();
        meio.setCustoMedio(new BigDecimal("9.0000"));
        produtoRepository.save(meio);

        vendaService.estornarVenda(venda.getCodVenda(), empresaId);

        ProdutoEntity depois = produtoRepository.findById(p.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("50").compareTo(depois.getQuantidadeEstoque()), "o estoque volta ao que era");
        // (40×9 + 10×6) / 50 = 8,40 — a devolução entra pelo custo ORIGINAL (6,00)
        assertEquals(0, new BigDecimal("8.4000").compareTo(depois.getCustoMedio()),
                "o item volta pelo custo com que saiu, não pelo custo de hoje");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. Produção transporta valor, não só quantidade
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void producaoDaAoProdutoOCustoRealDosInsumos() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity insumoA = criarProduto(empresaId, "TESTE-MP-A", TipoProduto.MP,
                BigDecimal.ZERO, new BigDecimal("2.00"), "100");
        ProdutoEntity insumoB = criarProduto(empresaId, "TESTE-MP-B", TipoProduto.MP,
                BigDecimal.ZERO, new BigDecimal("3.00"), "100");
        custeioService.definirCustoManual(insumoA, empresaId, new BigDecimal("2.00"));
        custeioService.definirCustoManual(insumoB, empresaId, new BigDecimal("3.00"));
        produtoRepository.save(insumoA);
        produtoRepository.save(insumoB);

        ProdutoEntity pf = criarProduto(empresaId, "TESTE-PF", TipoProduto.PF,
                new BigDecimal("30.00"), null, "0");

        // Receita: 2 de A + 1 de B  ->  custo unitário = 2×2 + 1×3 = 7,00
        vincular(pf, insumoA, new BigDecimal("2"));
        vincular(pf, insumoB, new BigDecimal("1"));

        ProducaoEntity producao = producaoService.realizarProducao(pf.getCodProduto(), new BigDecimal("10"), empresaId);

        assertEquals(0, new BigDecimal("70.00").compareTo(producao.getCustoTotal()),
                "custo total = 10 un × (2×2 + 1×3)");
        assertEquals(0, new BigDecimal("7.0000").compareTo(producao.getCustoUnitario()));

        ProdutoEntity pfDepois = produtoRepository.findById(pf.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(pfDepois.getQuantidadeEstoque()));
        assertEquals(0, new BigDecimal("7.0000").compareTo(pfDepois.getCustoMedio()),
                "o produto acabado tem que nascer com o custo dos insumos consumidos");

        // Insumos: saída reduz quantidade e preserva custo
        ProdutoEntity aDepois = produtoRepository.findById(insumoA.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("80").compareTo(aDepois.getQuantidadeEstoque()), "consumiu 2 × 10");
        assertEquals(0, new BigDecimal("2.0000").compareTo(aDepois.getCustoMedio()));
    }

    @Test
    @Transactional
    void estornoDeProducaoDevolveInsumoPeloCustoSnapshot() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity insumo = criarProduto(empresaId, "TESTE-MP-S", TipoProduto.MP,
                BigDecimal.ZERO, new BigDecimal("4.00"), "100");
        custeioService.definirCustoManual(insumo, empresaId, new BigDecimal("4.00"));
        produtoRepository.save(insumo);

        ProdutoEntity pf = criarProduto(empresaId, "TESTE-PF-S", TipoProduto.PF,
                new BigDecimal("50.00"), null, "0");
        vincular(pf, insumo, new BigDecimal("5"));

        ProducaoEntity producao = producaoService.realizarProducao(pf.getCodProduto(), new BigDecimal("10"), empresaId);
        assertEquals(0, new BigDecimal("50").compareTo(produtoRepository
                .findById(insumo.getCodProduto()).orElseThrow().getQuantidadeEstoque()),
                "consumiu 5 × 10");

        producaoService.estornar(producao.getCodProducao(), empresaId);

        ProdutoEntity depois = produtoRepository.findById(insumo.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(depois.getQuantidadeEstoque()), "insumo devolvido integralmente");
        assertEquals(0, new BigDecimal("4.0000").compareTo(depois.getCustoMedio()),
                "devolver pelo custo do snapshot mantém o custo médio estável");
    }

    private void vincular(ProdutoEntity pf, ProdutoEntity insumo, BigDecimal quantidade) {
        ProdutoMateriaPrimaEntity comp = new ProdutoMateriaPrimaEntity();
        comp.setId(new MateriaPrimaId(pf.getCodProduto(), insumo.getCodProduto()));
        comp.setProdutoFinal(pf);
        comp.setMateriaPrima(insumo);
        comp.setQuantidade(quantidade);
        materiaPrimaRepository.save(comp);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4b. ESTOQUE DECIMAL — consumo fracionário de insumo
    // ═══════════════════════════════════════════════════════════════════

    /**
     * O bug que motivou o estoque decimal: a produção fazia .intValue() na
     * quantidade consumida, então 0,350 kg de farinha dava baixa de ZERO.
     * Produzir 10 pães não tirava nada do estoque de farinha.
     */
    @Test
    @Transactional
    void producaoConsomeQuantidadeFracionariaDeInsumo() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity farinha = criarProduto(empresaId, "TESTE-FARINHA", TipoProduto.MP,
                BigDecimal.ZERO, new BigDecimal("4.00"), "100");
        custeioService.definirCustoManual(farinha, empresaId, new BigDecimal("4.00"));
        produtoRepository.save(farinha);

        ProdutoEntity pao = criarProduto(empresaId, "TESTE-PAO", TipoProduto.PF,
                new BigDecimal("2.00"), null, "0");
        vincular(pao, farinha, new BigDecimal("0.350")); // 350 g por unidade

        producaoService.realizarProducao(pao.getCodProduto(), new BigDecimal("10"), empresaId);

        ProdutoEntity depois = produtoRepository.findById(farinha.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("96.500").compareTo(depois.getQuantidadeEstoque()),
                "10 × 0,350 kg = 3,5 kg consumidos: 100 − 3,5 = 96,5 (antes o truncamento deixava 100)");

        ProdutoEntity paoDepois = produtoRepository.findById(pao.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("1.4000").compareTo(paoDepois.getCustoMedio()),
                "custo do pão = 0,350 kg × R$ 4,00 = R$ 1,40");
    }

    @Test
    @Transactional
    void estoqueGuardaTresCasasDecimais() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity p = criarProduto(empresaId, "TESTE-DECIMAL", TipoProduto.MP,
                BigDecimal.ZERO, new BigDecimal("10.00"), "0");

        // Compra 1,250 kg — três casas têm que sobreviver à ida e volta do banco
        contaPagarService.salvar(compra(p, "1.250", new BigDecimal("10.00")), empresaId);

        ProdutoEntity depois = produtoRepository.findById(p.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("1.250").compareTo(depois.getQuantidadeEstoque()),
                "1,250 kg não pode virar 1 nem 0");
        assertEquals(3, depois.getQuantidadeEstoque().scale(), "escala do estoque é 3 casas");
    }

    @Test
    @Transactional
    void vendaFracionadaDebitaEstoqueCorreto() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity queijo = criarProduto(empresaId, "TESTE-QUEIJO", TipoProduto.R,
                new BigDecimal("60.00"), new BigDecimal("30.00"), "10");
        custeioService.definirCustoManual(queijo, empresaId, new BigDecimal("30.00"));
        produtoRepository.save(queijo);

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodProduto(queijo.getCodProduto());
        item.setQuantidade(new BigDecimal("0.750")); // 750 g
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setItens(List.of(item));
        dto.setDataVenda(LocalDate.now());
        dto.setValorDinheiro(new BigDecimal("45.00"));
        VendaEntity venda = vendaService.registrarVenda(dto, empresaId);

        ProdutoEntity depois = produtoRepository.findById(queijo.getCodProduto()).orElseThrow();
        assertEquals(0, new BigDecimal("9.250").compareTo(depois.getQuantidadeEstoque()),
                "10 − 0,750 = 9,250");
        assertEquals(0, new BigDecimal("45.00").compareTo(venda.getValorTotal()),
                "0,750 × R$ 60,00 = R$ 45,00");

        var cmv = custeioService.custosDaVenda(venda.getCodVenda());
        assertEquals(0, new BigDecimal("30.0000").compareTo(cmv.get(queijo.getCodProduto())),
                "o CMV usa o custo por unidade de medida, não por peça");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Cascata de resolução e auditoria
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void custoMedioPrevaleceSobreComposicaoEValorCusto() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity insumo = criarProduto(empresaId, "TESTE-MP-R", TipoProduto.MP,
                BigDecimal.ZERO, new BigDecimal("10.00"), "100");
        custeioService.definirCustoManual(insumo, empresaId, new BigDecimal("10.00"));
        produtoRepository.save(insumo);

        ProdutoEntity pf = criarProduto(empresaId, "TESTE-PF-R", TipoProduto.PF,
                new BigDecimal("99.00"), new BigDecimal("1.00"), "0");
        vincular(pf, insumo, new BigDecimal("3")); // BOM diria 30,00

        // Sem custo médio: vale a composição (3 × 10 = 30), não o valor_custo (1,00)
        assertEquals(0, new BigDecimal("30.0000")
                        .compareTo(custeioService.custoUnitario(pf.getCodProduto(), empresaId)),
                "sem custo médio, a composição manda sobre o valor_custo");

        // Com custo médio: ele prevalece
        ProdutoEntity recarregado = produtoRepository.findById(pf.getCodProduto()).orElseThrow();
        recarregado.setCustoMedio(new BigDecimal("25.0000"));
        produtoRepository.save(recarregado);

        assertEquals(0, new BigDecimal("25.0000")
                        .compareTo(custeioService.custoUnitario(pf.getCodProduto(), empresaId)),
                "o custo médio é o custo real do que está em estoque — prevalece sobre a receita");
    }

    @Test
    @Transactional
    void cadaEntradaDeixaRastroNoHistorico() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity p = criarProduto(empresaId, "TESTE-CMP-H", TipoProduto.R,
                new BigDecimal("12.00"), new BigDecimal("5.00"), "10");
        custeioService.definirCustoManual(p, empresaId, new BigDecimal("5.00"));
        produtoRepository.save(p);

        contaPagarService.salvar(compra(p, "10", new BigDecimal("7.00")), empresaId);

        List<ProdutoCustoHistoricoEntity> historico =
                custeioService.historicoCusto(empresaId, p.getCodProduto());

        assertEquals(2, historico.size(), "1 ajuste manual + 1 compra");

        ProdutoCustoHistoricoEntity compra = historico.get(0); // mais recente primeiro
        assertEquals(OrigemEntradaEstoque.COMPRA, compra.getOrigem());
        assertEquals(0, new BigDecimal("10.0000").compareTo(compra.getQuantidadeAnterior()));
        assertEquals(0, new BigDecimal("5.0000").compareTo(compra.getCustoAnterior()));
        assertEquals(0, new BigDecimal("10.0000").compareTo(compra.getQuantidadeEntrada()));
        assertEquals(0, new BigDecimal("7.0000").compareTo(compra.getCustoEntrada()));
        assertEquals(0, new BigDecimal("20.0000").compareTo(compra.getQuantidadeNova()));
        assertEquals(0, new BigDecimal("6.0000").compareTo(compra.getCustoNovo()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. Integração com o CMV do item 1
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Transactional
    void cmvDaVendaUsaOCustoMedioAtualizadoPelaCompra() {
        Long empresaId = empresaId();
        if (empresaId == null) return;

        ProdutoEntity p = criarProduto(empresaId, "TESTE-CMP-I", TipoProduto.R,
                new BigDecimal("20.00"), new BigDecimal("5.00"), "10");
        custeioService.definirCustoManual(p, empresaId, new BigDecimal("5.00"));
        produtoRepository.save(p);

        // Compra encarece o custo médio para 6,00
        contaPagarService.salvar(compra(p, "10", new BigDecimal("7.00")), empresaId);

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodProduto(p.getCodProduto());
        item.setQuantidade(new BigDecimal("5"));
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setItens(List.of(item));
        dto.setDataVenda(LocalDate.now());
        dto.setValorDinheiro(new BigDecimal("100.00"));
        VendaEntity venda = vendaService.registrarVenda(dto, empresaId);

        var cmv = custeioService.custosDaVenda(venda.getCodVenda());
        assertEquals(0, new BigDecimal("6.0000").compareTo(cmv.get(p.getCodProduto())),
                "o CMV tem que usar o custo médio já ponderado pela compra, não o valor_custo antigo");
    }
}
