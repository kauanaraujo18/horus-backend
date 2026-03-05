package com.horus.projeto.barcode;

public class RelatorioRequest {
    private int quantidade;
    private int posicao; // Posição inicial (1 a 126)

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public int getPosicao() { return posicao; }
    public void setPosicao(int posicao) { this.posicao = posicao; }
}