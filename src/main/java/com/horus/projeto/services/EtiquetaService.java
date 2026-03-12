package com.horus.projeto.services;

import com.horus.projeto.entities.ProdutoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.repositories.ProdutoRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class EtiquetaService {

    @Autowired
    private ProdutoRepository repository;

    private static final String CAMINHO_LOGO = "https://horus-api-cjb4.onrender.com/images/logo2.png";

    public byte[] gerarEtiquetasPorId(Long codProduto, int quantidade) throws DocumentException, IOException {
        
        // ==========================================
        // 🛡️ 1. TRAVA DE PERFORMANCE E SEGURANÇA (Limite de 320)
        // ==========================================
        if (quantidade > 320) {
            throw new IllegalArgumentException("O limite máximo permitido é de 320 etiquetas por impressão.");
        }

        // 2. Busca o Produto no Banco
        ProdutoEntity produto = repository.findById(codProduto)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado. ID: " + codProduto));

        // ==========================================
        // 🛡️ 3. BLINDAGEM MULTI-TENANT (Isolamento de Empresas)
        // ==========================================
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();

        if (!produto.getEmpresa().getId().equals(idEmpresaLogada)) {
            throw new RuntimeException("Acesso Negado: Este produto pertence a outra empresa.");
        }

        // 4. Configuração Inicial do PDF
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        
        // Ajuste de layout: remove 1 pixel da altura para evitar quebra de página errada
        float alturaCelula = (document.getPageSize().getHeight() / 4f) - 1f;

        // Fontes do PDF
        Font fonteProduto = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.WHITE);
        Font fontePreco = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.WHITE);
        Font fonteErro = new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, BaseColor.RED);

        // 5. Formatação Segura de Moeda
        String textoValor;
        if (produto.getValor() != null) {
            @SuppressWarnings("deprecation")
            NumberFormat formatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
            textoValor = formatoMoeda.format(produto.getValor());
        } else {
            textoValor = "R$ --,--"; 
        }

        // ==========================================
        // 🚀 6. OTIMIZAÇÃO: Cache da Logo na Memória
        // ==========================================
        // Fazemos o download da logo APENAS UMA VEZ antes do laço iniciar.
        Image logoCache = null;
        try {
            logoCache = Image.getInstance(CAMINHO_LOGO);
            logoCache.scaleToFit(80, 40);
            logoCache.setAlignment(Element.ALIGN_CENTER);
        } catch (Exception e) {
            System.out.println("Aviso: Não foi possível carregar a logo das etiquetas.");
        }

        // 7. Geração em Lote das Etiquetas
        for (int i = 0; i < quantidade; i++) {
            PdfPCell cell = new PdfPCell();
            cell.setFixedHeight(alturaCelula);
            cell.setBackgroundColor(BaseColor.BLACK);
            cell.setBorderColor(BaseColor.WHITE);
            cell.setBorderWidth(1f);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(10);

            // LOGO: Usa a imagem já salva na variável logoCache
            if (logoCache != null) {
                cell.addElement(logoCache);
            }
            cell.addElement(new Paragraph(" ")); 

            // NOME E VALOR
            Paragraph pNome = new Paragraph(produto.getNome(), fonteProduto);
            pNome.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(pNome);

            Paragraph pValor = new Paragraph(textoValor, fontePreco);
            pValor.setAlignment(Element.ALIGN_CENTER);
            pValor.setSpacingBefore(5);
            cell.addElement(pValor);

            cell.addElement(new Paragraph(" "));

            // CÓDIGO DE BARRAS (Box Branco)
            PdfPTable barcodeTable = new PdfPTable(1);
            barcodeTable.setWidthPercentage(90);
            PdfPCell barcodeCell = new PdfPCell();
            barcodeCell.setBackgroundColor(BaseColor.WHITE);
            barcodeCell.setBorder(Rectangle.NO_BORDER);
            barcodeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            barcodeCell.setPadding(5);
            barcodeCell.setPaddingBottom(8);

            // Validação EAN-13
            if (isEan13Valid(produto.getCodigo())) {
                PdfContentByte cb = writer.getDirectContent();
                BarcodeEAN codeEAN = new BarcodeEAN();
                codeEAN.setCodeType(Barcode.EAN13);
                codeEAN.setCode(produto.getCodigo()); 
                
                Image imageEAN = codeEAN.createImageWithBarcode(cb, BaseColor.BLACK, BaseColor.BLACK);
                imageEAN.scalePercent(130); 
                imageEAN.setAlignment(Element.ALIGN_CENTER);
                barcodeCell.addElement(imageEAN);
            } else {
                Paragraph pErro = new Paragraph("Cód: " + produto.getCodigo() + "\n(Sem EAN-13)", fonteErro);
                pErro.setAlignment(Element.ALIGN_CENTER);
                barcodeCell.addElement(pErro);
            }

            barcodeTable.addCell(barcodeCell);
            cell.addElement(barcodeTable);
            table.addCell(cell);
        }

        table.completeRow();
        document.add(table);
        document.close();

        return out.toByteArray();
    }

    private boolean isEan13Valid(String codigo) {
        if (codigo == null) return false;
        return codigo.matches("\\d{12,13}");
    }
}