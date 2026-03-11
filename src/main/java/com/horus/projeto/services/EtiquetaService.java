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
        
        // 1. Busca no Banco
        ProdutoEntity produto = repository.findById(codProduto)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado. ID: " + codProduto));

        // ==========================================
        // 🛡️ BLINDAGEM MULTI-TENANT (SaaS)
        // ==========================================
        // Captura o usuário logado no momento da requisição
        var usuarioLogado = (UsuarioEntity) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long idEmpresaLogada = usuarioLogado.getEmpresa().getId();

        // Verifica se a empresa dona do produto é a mesma do usuário logado
        if (!produto.getEmpresa().getId().equals(idEmpresaLogada)) {
            throw new RuntimeException("Acesso Negado: Este produto pertence a outra empresa.");
        }
        // ==========================================

        // 2. Configura PDF
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.open();

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        float alturaCelula = document.getPageSize().getHeight() / 4f;

        // Fontes
        Font fonteProduto = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.WHITE);
        Font fontePreco = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.WHITE);
        Font fonteErro = new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, BaseColor.RED);

        // 3. Formatação de Preço Segura
        String textoValor;
        if (produto.getValor() != null) {
            @SuppressWarnings("deprecation")
            NumberFormat formatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
            textoValor = formatoMoeda.format(produto.getValor());
        } else {
            textoValor = "R$ --,--"; 
        }

        // 4. Loop de Etiquetas
        for (int i = 0; i < quantidade; i++) {
            PdfPCell cell = new PdfPCell();
            cell.setFixedHeight(alturaCelula);
            cell.setBackgroundColor(BaseColor.BLACK);
            cell.setBorderColor(BaseColor.WHITE);
            cell.setBorderWidth(1f);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(10);

            // LOGO
            try {
                Image img = Image.getInstance(CAMINHO_LOGO);
                img.scaleToFit(80, 40);
                img.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(img);
            } catch (Exception e) {
                // Ignora imagem se der erro
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

            // Validação e Geração (Usando String direto)
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