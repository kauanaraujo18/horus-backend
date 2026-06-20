package com.horus.projeto.enums;

/**
 * Nível hierárquico da classe financeira.
 * SINTETICA: apenas agrupa (nunca recebe lançamento direto).
 * ANALITICA: conta folha — recebe lançamentos e amarrações de produto/conta.
 */
public enum NivelClasse {
    SINTETICA,
    ANALITICA
}
