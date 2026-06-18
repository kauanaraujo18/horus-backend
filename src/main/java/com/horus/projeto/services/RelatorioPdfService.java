package com.horus.projeto.services;

import com.horus.projeto.dto.RelatorioContasPagarFilterDTO;
import com.horus.projeto.dto.RelatorioVendasFilterDTO;
import com.horus.projeto.entities.ContaPagarEntity;
import com.horus.projeto.entities.ContaPagarItemEntity;
import com.horus.projeto.entities.ContaPagarParcelaEntity;
import com.horus.projeto.dto.VendaResponseDTO;
import com.horus.projeto.dto.ItemVendaResponseDTO;
import com.horus.projeto.repositories.ContaPagarRepository;
import com.horus.projeto.repositories.VendaRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatorioPdfService {

    private final VendaRepository vendaRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final VendaService vendaService;

    // ── Cores ────────────────────────────────────────────────────────────────
    private static final BaseColor C_HEADER  = new BaseColor(17,  24,  39);
    private static final BaseColor C_SECTION = new BaseColor(249, 250, 251);
    private static final BaseColor C_BORDER  = new BaseColor(229, 231, 235);
    private static final BaseColor C_MUTED   = new BaseColor(107, 114, 128);
    private static final BaseColor C_INFO    = new BaseColor(243, 244, 246);
    private static final BaseColor C_PAGA    = new BaseColor(209, 250, 229);
    private static final BaseColor C_VENCIDA = new BaseColor(254, 226, 226);
    private static final BaseColor C_ABERTO  = new BaseColor(254, 243, 199);
    private static final BaseColor C_TOTAL   = new BaseColor(31,  41,  55);

    // ── Fontes ───────────────────────────────────────────────────────────────
    private Font fTitle()   { return new Font(Font.FontFamily.HELVETICA, 15, Font.BOLD,   BaseColor.WHITE); }
    private Font fSub()     { return new Font(Font.FontFamily.HELVETICA,  9, Font.NORMAL, new BaseColor(156,163,175)); }
    private Font fNormal()  { return new Font(Font.FontFamily.HELVETICA,  9, Font.NORMAL); }
    private Font fBold()    { return new Font(Font.FontFamily.HELVETICA,  9, Font.BOLD); }
    private Font fSmall()   { return new Font(Font.FontFamily.HELVETICA,  8, Font.NORMAL, C_MUTED); }
    private Font fTh()      { return new Font(Font.FontFamily.HELVETICA,  8, Font.BOLD,   C_MUTED); }
    private Font fTotal()   { return new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,   BaseColor.WHITE); }
    private Font fInfo()    { return new Font(Font.FontFamily.HELVETICA,  8, Font.NORMAL, new BaseColor(75,85,99)); }
    private Font fInfoBold(){ return new Font(Font.FontFamily.HELVETICA,  8, Font.BOLD,   new BaseColor(31,41,55)); }

    // ═══════════════════════════════════════════════════════════════════════
    // PDF RELATÓRIO DE VENDAS
    // ═══════════════════════════════════════════════════════════════════════

    public byte[] gerarPdfVendas(RelatorioVendasFilterDTO filtro, Long empresaId) throws Exception {
        List<VendaResponseDTO> todas = vendaService.listarPorEmpresa(empresaId);

        LocalDate inicio = LocalDate.parse(filtro.getDataInicio());
        LocalDate fim    = LocalDate.parse(filtro.getDataFim());
        Set<Long> prodIds = filtro.getProdutoIds() != null
                ? filtro.getProdutoIds().stream().collect(Collectors.toSet()) : Set.of();

        List<VendaResponseDTO> filtradas = todas.stream().filter(v -> {
            LocalDate dv = v.getDataVenda().toLocalDate();
            if (dv.isBefore(inicio) || dv.isAfter(fim)) return false;

            String fp = filtro.getFormaPagamento();
            if (!"TODAS".equalsIgnoreCase(fp)) {
                if ("DINHEIRO".equals(fp) && isZero(v.getValorDinheiro())) return false;
                if ("PIX".equals(fp)      && isZero(v.getValorPix()))      return false;
                if ("CREDITO".equals(fp)  && isZero(v.getValorCredito()))  return false;
                if ("DEBITO".equals(fp)   && isZero(v.getValorDebito()))   return false;
            }

            if (prodIds.isEmpty()) return false;
            return v.getItens() != null && v.getItens().stream()
                    .anyMatch(i -> prodIds.contains(i.getCodProduto()));
        }).collect(Collectors.toList());

        if (filtradas.isEmpty())
            throw new IllegalArgumentException("Nenhuma venda encontrada com os filtros informados.");

        Document doc = new Document(PageSize.A4, 36, 36, 50, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        // Cabeçalho
        adicionarCabecalho(doc, "RELATÓRIO ANALÍTICO DE VENDAS");

        // Filtros
        String fp = filtro.getFormaPagamento() == null ? "TODAS" : filtro.getFormaPagamento();
        adicionarBlocoFiltros(doc, new String[][]{
            {"Período", fmtData(inicio) + " a " + fmtData(fim)},
            {"Pagamento", fp},
            {"Emitido em", fmtDataHora(LocalDateTime.now())}
        });

        // Conteúdo
        BigDecimal totalGeral = BigDecimal.ZERO;
        for (VendaResponseDTO v : filtradas) {
            totalGeral = totalGeral.add(nvl(v.getValorTotal()));
            adicionarVendaBlock(doc, v, prodIds);
        }

        // Rodapé totalizador
        adicionarRodapeTotalUnico(doc, "TOTAL VENDIDO NO PERÍODO", totalGeral);

        doc.close();
        return baos.toByteArray();
    }

    private void adicionarVendaBlock(Document doc, VendaResponseDTO v, Set<Long> prodIds) throws Exception {
        // Espaço
        doc.add(new Paragraph(" ", fSmall()));

        // Header da venda
        PdfPTable header = new PdfPTable(new float[]{1, 1});
        header.setWidthPercentage(100);
        header.setSpacingBefore(0);

        String dataF = fmtDataHora(v.getDataVenda());
        Phrase phLeft = new Phrase();
        phLeft.add(new Chunk("Venda #" + v.getCodVenda(), fBold()));
        phLeft.add(new Chunk("  " + dataF, fSmall()));
        PdfPCell cLeft = cellPad(phLeft, C_SECTION, Element.ALIGN_LEFT, 8f);
        cLeft.setBorderColor(C_BORDER);

        PdfPCell cRight = cellPad(
            new Phrase("Total: " + fmtMoeda(v.getValorTotal()), fBold()), C_SECTION, Element.ALIGN_RIGHT, 8f);
        cRight.setBorderColor(C_BORDER);

        header.addCell(cLeft);
        header.addCell(cRight);
        doc.add(header);

        // Info da venda
        StringBuilder info = new StringBuilder();
        if (!isZero(v.getValorDinheiro())) info.append("Dinheiro ");
        if (!isZero(v.getValorPix()))      info.append("PIX ");
        if (!isZero(v.getValorCredito()))  info.append("Crédito ");
        if (!isZero(v.getValorDebito()))   info.append("Débito ");
        if (info.length() == 0) info.append("—");

        PdfPTable infoRow = new PdfPTable(1);
        infoRow.setWidthPercentage(100);
        String infoText = "Forma(s): " + info.toString().trim()
                + "  |  Itens: " + (v.getItens() != null ? v.getItens().size() : 0)
                + "  |  Desconto: " + fmtMoeda(v.getDesconto())
                + "  |  Acréscimo: " + fmtMoeda(v.getAcrescimo());
        PdfPCell cInfo = cellPad(new Phrase(infoText, fInfo()), C_INFO, Element.ALIGN_LEFT, 6f);
        cInfo.setBorderColor(C_BORDER);
        cInfo.setBorderWidthTop(0);
        infoRow.addCell(cInfo);
        doc.add(infoRow);

        // Tabela de itens
        PdfPTable tabItens = new PdfPTable(new float[]{5, 1, 2, 2});
        tabItens.setWidthPercentage(100);
        addThRow(tabItens, "Produto", "Qtd", "Unit.", "Subtotal");

        List<ItemVendaResponseDTO> itens = v.getItens();
        if (itens != null) {
            for (ItemVendaResponseDTO item : itens) {
                if (!prodIds.contains(item.getCodProduto())) continue;
                tabItens.addCell(tdLeft(item.getNome() != null ? item.getNome() : "—"));
                tabItens.addCell(tdCenter(String.valueOf(item.getQuantidade())));
                tabItens.addCell(tdRight(fmtMoeda(item.getValorUnitario())));
                tabItens.addCell(tdRight(fmtMoeda(item.getValorTotalItem())));
            }
        }
        doc.add(tabItens);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PDF RELATÓRIO DE CONTAS A PAGAR
    // ═══════════════════════════════════════════════════════════════════════

    public byte[] gerarPdfContasPagar(RelatorioContasPagarFilterDTO filtro, Long empresaId) throws Exception {
        List<ContaPagarEntity> todas = contaPagarRepository
                .findByEmpresaIdOrderByDataRegistroDesc(empresaId);

        LocalDate inicio = LocalDate.parse(filtro.getVencInicio());
        LocalDate fim    = LocalDate.parse(filtro.getVencFim());
        Set<Long> prodIds = filtro.getProdutoIds() != null
                ? filtro.getProdutoIds().stream().collect(Collectors.toSet()) : Set.of();

        List<ContaPagarEntity> filtradas = todas.stream().filter(c -> {
            LocalDate venc = c.getDataVencimento();
            if (venc == null || venc.isBefore(inicio) || venc.isAfter(fim)) return false;

            String st = filtro.getStatus();
            if (!"TODOS".equalsIgnoreCase(st) && !c.getStatus().equals(st)) return false;

            if (prodIds.isEmpty()) return false;
            return c.getItens() != null && c.getItens().stream()
                    .anyMatch(i -> i.getProduto() != null && prodIds.contains(i.getProduto().getCodProduto()));
        }).collect(Collectors.toList());

        if (filtradas.isEmpty())
            throw new IllegalArgumentException("Nenhuma conta a pagar encontrada com os filtros informados.");

        Document doc = new Document(PageSize.A4, 36, 36, 50, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        adicionarCabecalho(doc, "RELATÓRIO DE CONTAS A PAGAR");

        String st = filtro.getStatus() == null ? "TODOS" : filtro.getStatus();
        adicionarBlocoFiltros(doc, new String[][]{
            {"Vencimento", fmtData(inicio) + " a " + fmtData(fim)},
            {"Status", st.equals("TODOS") ? "Todos" : st},
            {"Emitido em", fmtDataHora(LocalDateTime.now())}
        });

        BigDecimal totalGeral = BigDecimal.ZERO;
        BigDecimal totalPago  = BigDecimal.ZERO;
        BigDecimal totalAberto= BigDecimal.ZERO;

        for (ContaPagarEntity c : filtradas) {
            BigDecimal vt = nvl(c.getValorTotal());
            totalGeral = totalGeral.add(vt);
            if ("PAGA".equals(c.getStatus())) totalPago   = totalPago.add(vt);
            else                               totalAberto = totalAberto.add(vt);
            adicionarContaBlock(doc, c, prodIds);
        }

        adicionarRodapeTresLinhas(doc, totalPago, totalAberto, totalGeral);

        doc.close();
        return baos.toByteArray();
    }

    private void adicionarContaBlock(Document doc, ContaPagarEntity c, Set<Long> prodIds) throws Exception {
        doc.add(new Paragraph(" ", fSmall()));

        // Status color
        BaseColor statusBg = switch (c.getStatus()) {
            case "PAGA"    -> C_PAGA;
            case "VENCIDA" -> C_VENCIDA;
            default        -> C_ABERTO;
        };

        // Header
        PdfPTable header = new PdfPTable(new float[]{6, 1});
        header.setWidthPercentage(100);

        Phrase phDesc = new Phrase();
        phDesc.add(new Chunk("Conta #" + c.getCodContaPagar() + "  ", fBold()));
        phDesc.add(new Chunk(c.getDescricao(), fNormal()));
        PdfPCell cDesc = cellPad(phDesc, C_SECTION, Element.ALIGN_LEFT, 8f);
        cDesc.setBorderColor(C_BORDER);

        PdfPCell cStatus = cellPad(new Phrase(c.getStatus(), new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD)),
                statusBg, Element.ALIGN_CENTER, 8f);
        cStatus.setBorderColor(C_BORDER);

        header.addCell(cDesc);
        header.addCell(cStatus);
        doc.add(header);

        // Info
        PdfPTable infoRow = new PdfPTable(1);
        infoRow.setWidthPercentage(100);
        String infoText = "Fornecedor: " + nvlStr(c.getFornecedor())
                + "  |  NF: " + nvlStr(c.getNumeroNotaFiscal())
                + "  |  Vencimento: " + (c.getDataVencimento() != null ? fmtData(c.getDataVencimento()) : "—")
                + "  |  Total: " + fmtMoeda(c.getValorTotal());
        PdfPCell cInfo = cellPad(new Phrase(infoText, fInfo()), C_INFO, Element.ALIGN_LEFT, 6f);
        cInfo.setBorderColor(C_BORDER);
        cInfo.setBorderWidthTop(0);
        infoRow.addCell(cInfo);
        doc.add(infoRow);

        // Itens
        PdfPTable tabItens = new PdfPTable(new float[]{5, 1, 2, 2});
        tabItens.setWidthPercentage(100);
        addSecaoTitulo(tabItens, "ITENS", 4);
        addThRow(tabItens, "Produto", "Qtd", "Unit.", "Subtotal");

        boolean temItem = false;
        for (ContaPagarItemEntity item : c.getItens()) {
            if (item.getProduto() == null || !prodIds.contains(item.getProduto().getCodProduto())) continue;
            temItem = true;
            tabItens.addCell(tdLeft(item.getProduto().getNome()));
            tabItens.addCell(tdCenter(String.valueOf(item.getQuantidade())));
            tabItens.addCell(tdRight(fmtMoeda(item.getValorUnitario())));
            tabItens.addCell(tdRight(fmtMoeda(item.getValorTotalItem())));
        }
        if (!temItem) {
            PdfPCell sem = tdLeft("Sem itens para os produtos selecionados");
            sem.setColspan(4);
            tabItens.addCell(sem);
        }
        doc.add(tabItens);

        // Parcelas
        PdfPTable tabParcelas = new PdfPTable(new float[]{1, 2, 2, 2});
        tabParcelas.setWidthPercentage(100);
        addSecaoTitulo(tabParcelas, "PARCELAS", 4);
        addThRow(tabParcelas, "Nº", "Vencimento", "Valor", "Situação");

        if (c.getParcelas() == null || c.getParcelas().isEmpty()) {
            PdfPCell sem = tdLeft("Sem parcelas cadastradas");
            sem.setColspan(4);
            tabParcelas.addCell(sem);
        } else {
            for (ContaPagarParcelaEntity p : c.getParcelas()) {
                tabParcelas.addCell(tdCenter(p.getNumeroParcela() + "ª"));
                tabParcelas.addCell(tdCenter(p.getDataVencimento() != null
                        ? fmtData(p.getDataVencimento()) : "—"));
                tabParcelas.addCell(tdRight(fmtMoeda(p.getValorParcela())));
                PdfPCell cSit = tdCenter(Boolean.TRUE.equals(p.getPaga()) ? "Paga" : "Em aberto");
                cSit.setBackgroundColor(Boolean.TRUE.equals(p.getPaga()) ? C_PAGA : C_ABERTO);
                tabParcelas.addCell(cSit);
            }
        }
        doc.add(tabParcelas);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS VISUAIS
    // ═══════════════════════════════════════════════════════════════════════

    private void adicionarCabecalho(Document doc, String titulo) throws DocumentException {
        PdfPTable tbl = new PdfPTable(1);
        tbl.setWidthPercentage(100);
        tbl.setSpacingAfter(12);

        PdfPCell cTitulo = new PdfPCell(new Phrase(titulo, fTitle()));
        cTitulo.setBackgroundColor(C_HEADER);
        cTitulo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cTitulo.setPadding(14);
        cTitulo.setBorder(Rectangle.NO_BORDER);
        tbl.addCell(cTitulo);

        PdfPCell cSub = new PdfPCell(new Phrase("Sistema de Gestão Horus", fSub()));
        cSub.setBackgroundColor(C_HEADER);
        cSub.setHorizontalAlignment(Element.ALIGN_CENTER);
        cSub.setPaddingBottom(10);
        cSub.setPaddingTop(0);
        cSub.setPaddingLeft(14);
        cSub.setPaddingRight(14);
        cSub.setBorder(Rectangle.NO_BORDER);
        tbl.addCell(cSub);

        doc.add(tbl);
    }

    private void adicionarBlocoFiltros(Document doc, String[][] linhas) throws DocumentException {
        PdfPTable tbl = new PdfPTable(1);
        tbl.setWidthPercentage(100);
        tbl.setSpacingAfter(12);

        Phrase conteudo = new Phrase();
        for (int i = 0; i < linhas.length; i++) {
            conteudo.add(new Chunk(linhas[i][0] + ": ", fInfoBold()));
            conteudo.add(new Chunk(linhas[i][1], fInfo()));
            if (i < linhas.length - 1) conteudo.add(new Chunk("     ", fInfo()));
        }

        PdfPCell cell = new PdfPCell(conteudo);
        cell.setBackgroundColor(C_INFO);
        cell.setPadding(10);
        cell.setBorderColor(C_BORDER);
        tbl.addCell(cell);
        doc.add(tbl);
    }

    private void adicionarRodapeTotalUnico(Document doc, String label, BigDecimal valor) throws DocumentException {
        PdfPTable tbl = new PdfPTable(new float[]{3, 1});
        tbl.setWidthPercentage(100);
        tbl.setSpacingBefore(16);

        PdfPCell cLabel = cellPad(new Phrase(label, fTotal()), C_TOTAL, Element.ALIGN_LEFT, 12f);
        cLabel.setBorder(Rectangle.NO_BORDER);
        PdfPCell cValor = cellPad(new Phrase(fmtMoeda(valor), fTotal()), C_TOTAL, Element.ALIGN_RIGHT, 12f);
        cValor.setBorder(Rectangle.NO_BORDER);

        tbl.addCell(cLabel);
        tbl.addCell(cValor);
        doc.add(tbl);
    }

    private void adicionarRodapeTresLinhas(Document doc, BigDecimal pago, BigDecimal aberto, BigDecimal total)
            throws DocumentException {
        PdfPTable tbl = new PdfPTable(new float[]{3, 1});
        tbl.setWidthPercentage(100);
        tbl.setSpacingBefore(16);

        addSummaryRow(tbl, "Total Pago", pago, C_PAGA,
                new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(6,95,70)));
        addSummaryRow(tbl, "Total em Aberto / Vencido", aberto, C_ABERTO,
                new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, new BaseColor(120,53,15)));
        addSummaryRow(tbl, "TOTAL GERAL DO PERÍODO", total, C_TOTAL, fTotal());

        doc.add(tbl);
    }

    private void addSummaryRow(PdfPTable tbl, String label, BigDecimal valor, BaseColor bg, Font font) {
        PdfPCell cL = cellPad(new Phrase(label, font), bg, Element.ALIGN_LEFT, 10f);
        cL.setBorderColor(C_BORDER);
        PdfPCell cV = cellPad(new Phrase(fmtMoeda(valor), font), bg, Element.ALIGN_RIGHT, 10f);
        cV.setBorderColor(C_BORDER);
        tbl.addCell(cL);
        tbl.addCell(cV);
    }

    private void addSecaoTitulo(PdfPTable tbl, String titulo, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(titulo, fTh()));
        c.setBackgroundColor(C_INFO);
        c.setPadding(5);
        c.setPaddingLeft(8);
        c.setColspan(colspan);
        c.setBorderColor(C_BORDER);
        c.setBorderWidthTop(0);
        tbl.addCell(c);
    }

    private void addThRow(PdfPTable tbl, String... headers) {
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, fTh()));
            c.setBackgroundColor(C_SECTION);
            c.setPadding(6);
            c.setPaddingLeft(8);
            c.setBorderColor(C_BORDER);
            c.setBorderWidthTop(0);
            tbl.addCell(c);
        }
    }

    private PdfPCell tdLeft(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, fNormal()));
        c.setPadding(7); c.setPaddingLeft(8);
        c.setBorderColor(C_BORDER);
        c.setBorderWidthTop(0);
        return c;
    }

    private PdfPCell tdCenter(String text) {
        PdfPCell c = tdLeft(text);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private PdfPCell tdRight(String text) {
        PdfPCell c = tdLeft(text);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPaddingRight(8);
        return c;
    }

    private PdfPCell cellPad(Phrase phrase, BaseColor bg, int align, float padding) {
        PdfPCell c = new PdfPCell(phrase);
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(align);
        c.setPadding(padding);
        return c;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITÁRIOS
    // ═══════════════════════════════════════════════════════════════════════

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DT   = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String fmtData(LocalDate d)     { return d != null ? d.format(FMT_DATE) : "—"; }
    private String fmtDataHora(LocalDateTime dt) { return dt != null ? dt.format(FMT_DT) : "—"; }

    private String fmtMoeda(BigDecimal v) {
        if (v == null) return "R$ 0,00";
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(v);
    }

    private BigDecimal nvl(BigDecimal v)  { return v != null ? v : BigDecimal.ZERO; }
    private String     nvlStr(String s)   { return s != null && !s.isBlank() ? s : "—"; }
    private boolean    isZero(BigDecimal v){ return v == null || v.compareTo(BigDecimal.ZERO) == 0; }
}
