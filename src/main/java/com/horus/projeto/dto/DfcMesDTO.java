package com.horus.projeto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Um mês da grade do DFC (coluna dinâmica). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DfcMesDTO {
    private String chave;  // "2026-04"
    private String label;  // "Abr/26"
}
