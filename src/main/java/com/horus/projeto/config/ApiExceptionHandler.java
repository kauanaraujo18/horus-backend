package com.horus.projeto.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduz falhas de integridade do banco em mensagem legível.
 *
 * Antes disso, uma violação de constraint virava HTTP 500 sem corpo
 * (server.error.include-message=never), e o frontend só conseguia mostrar
 * "Verifique os dados e tente novamente" — o que escondeu por completo a causa
 * real de "não consigo cadastrar produto sem EAN" (coluna NOT NULL no banco).
 *
 * O detalhe técnico vai para o log; ao usuário vai uma explicação acionável,
 * sem vazar nomes de constraint nem SQL.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integridade(DataIntegrityViolationException e) {
        String detalhe = causaRaiz(e).toLowerCase();
        log.error("Violação de integridade no banco: {}", causaRaiz(e), e);

        String mensagem;
        if (detalhe.contains("null value") && detalhe.contains("codigo")) {
            mensagem = "Este banco ainda exige o código (EAN/SKU) do produto. "
                     + "Rode a migração db/produto_codigo_v1.sql para torná-lo opcional.";
        } else if (detalhe.contains("duplicate key") || detalhe.contains("unique")) {
            mensagem = "Já existe um registro com esse valor único (ex.: código de produto repetido).";
        } else if (detalhe.contains("null value")) {
            mensagem = "Um campo obrigatório não foi preenchido. "
                     + "Se o formulário parece completo, pode haver migração de banco pendente "
                     + "— confira em /api/health/schema.";
        } else if (detalhe.contains("foreign key") || detalhe.contains("violates foreign key")) {
            mensagem = "O registro está vinculado a outro e não pode ser alterado ou removido.";
        } else {
            mensagem = "O banco recusou a operação por uma regra de integridade. "
                     + "Confira migrações pendentes em /api/health/schema.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", mensagem));
    }

    private String causaRaiz(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getMessage() != null ? c.getMessage() : t.toString();
    }
}
