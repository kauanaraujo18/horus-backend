package com.horus.projeto.barcode;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BarcodeEAN;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service // Transformamos em um serviço do Spring
public class GeradorCodigoBarras {

    public byte[] gerarPdfStream(int quantidade, int posicaoInicial) throws DocumentException {
        
        // 1. Gera lista com dados ALEATÓRIOS
        List<String> listaAleatoria = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < quantidade; i++) {
            listaAleatoria.add(gerarEan13Valido(random));
        }

        // 2. Prepara o documento em memória (ByteArrayOutputStream)
        Document document = new Document(PageSize.A4, 10, 10, 15, 15);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        document.open();
        
        PdfContentByte cb = writer.getDirectContent();

        // 3. Configuração da Tabela
        PdfPTable tabela = new PdfPTable(7); // 7 Colunas
        tabela.setWidthPercentage(100);
        
        // --- LÓGICA DA POSIÇÃO INICIAL ---
        // Se a posição for 3, precisamos pular a 1 e a 2 (criar células vazias)
        int celulasParaPular = posicaoInicial - 1;
        
        // Célula vazia padrão
        PdfPCell cellVazia = new PdfPCell();
        cellVazia.setBorder(Rectangle.NO_BORDER);
        // Calcula altura aproximada para manter alinhamento (opcional, mas bom visualmente)
        float alturaCelula = (document.getPageSize().getHeight() - 30) / 18f;
        cellVazia.setFixedHeight(alturaCelula);

        // Adiciona as células vazias iniciais
        for (int i = 0; i < celulasParaPular; i++) {
            tabela.addCell(cellVazia);
        }

        // --- GERAÇÃO DAS ETIQUETAS ---
        float larguraPaginaUtil = document.getPageSize().getWidth() - 20;
        float larguraCelula = larguraPaginaUtil / 7f;

        for (String numero : listaAleatoria) {
            PdfPCell cell = criarCelulaBarcode(cb, numero, larguraCelula, alturaCelula);
            tabela.addCell(cell);
        }

        // Completa a linha final com vazias se necessário (para fechar a borda da tabela corretamente)
        int totalCelulasPreenchidas = celulasParaPular + quantidade;
        int restoLinha = 7 - (totalCelulasPreenchidas % 7);
        if (restoLinha < 7) {
            for (int i = 0; i < restoLinha; i++) {
                tabela.addCell(cellVazia);
            }
        }

        document.add(tabela);
        document.close();

        return outputStream.toByteArray();
    }

    // Método auxiliar para criar a célula com o código de barras
    private PdfPCell criarCelulaBarcode(PdfContentByte cb, String numero, float largura, float altura) {
        BarcodeEAN code = new BarcodeEAN();
        code.setCodeType(BarcodeEAN.EAN13);
        code.setCode(numero);
        code.setSize(8f);
        code.setBaseline(8f);

        PdfPCell cell;
        try {
            Image img = code.createImageWithBarcode(cb, null, null);
            img.scaleAbsolute(largura - 4, altura - 10); // Ajuste fino de margem interna
            cell = new PdfPCell(img);
        } catch (Exception e) {
            cell = new PdfPCell(new com.itextpdf.text.Paragraph("ERRO"));
        }

        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setFixedHeight(altura);
        cell.setBorder(Rectangle.NO_BORDER); // Sem bordas conforme seu original
        cell.setPadding(2);
        
        return cell;
    }

    // Seu método original mantido
    public static String gerarEan13Valido(Random random) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 12; k++) {
            sb.append(random.nextInt(10));
        }
        String dozeDigitos = sb.toString();
        int soma = 0;
        for (int k = 0; k < 12; k++) {
            int digito = Character.getNumericValue(dozeDigitos.charAt(k));
            if (k % 2 == 0) soma += digito * 1;
            else soma += digito * 3;
        }
        int resto = soma % 10;
        int checkDigit = (resto == 0) ? 0 : (10 - resto);
        return dozeDigitos + checkDigit;
    }
}