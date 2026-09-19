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
 * Traduz falhas de integridade do banco em mensagem legível para o usuário final.
 *
 * Antes disso, uma violação de constraint virava HTTP 500 sem corpo
 * (server.error.include-message=never) e o frontend não tinha o que mostrar.
 *
 * A mensagem exibida fala a língua de quem usa o sistema — nada de nome de
 * constraint, SQL ou instrução de manutenção de banco. O detalhe técnico fica
 * no log do servidor, que é onde quem mantém o sistema vai procurar.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integridade(DataIntegrityViolationException e) {
        String detalhe = causaRaiz(e).toLowerCase();
        log.error("Violação de integridade no banco: {}", causaRaiz(e), e);

        String mensagem;
        if (detalhe.contains("duplicate key") || detalhe.contains("unique")) {
            mensagem = "Já existe um registro com esse valor (ex.: código de produto repetido).";
        } else if (detalhe.contains("null value")) {
            mensagem = "Não foi possível salvar: um campo obrigatório não foi preenchido.";
        } else if (detalhe.contains("foreign key")) {
            mensagem = "O registro está vinculado a outro e não pode ser alterado ou removido.";
        } else {
            mensagem = "Não foi possível salvar. Verifique os dados e tente novamente.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", mensagem));
    }

    private String causaRaiz(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getMessage() != null ? c.getMessage() : t.toString();
    }
}
