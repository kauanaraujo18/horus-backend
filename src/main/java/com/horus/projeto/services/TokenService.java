package com.horus.projeto.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.horus.projeto.entities.UsuarioEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class TokenService {

    // O Render vai injetar a senha de produção aqui. Se não achar, usa a local.
    @Value("${api.security.token.secret:minha-chave-secreta-horus-2026}")
    private String secret;

    public String gerarToken(UsuarioEntity usuario) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("horus-api")
                    .withSubject(usuario.getLogin()) 
                    .withClaim("empresaId", usuario.getEmpresa().getId()) 
                    .withExpiresAt(gerarDataExpiracao())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token jwt", exception);
        }
    }

    public String getSubject(String tokenJWT) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("horus-api")
                    .build()
                    .verify(tokenJWT)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("Token JWT inválido ou expirado!");
        }
    }

    public Long getEmpresaId(String tokenJWT) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("horus-api")
                    .build()
                    .verify(tokenJWT)
                    .getClaim("empresaId").asLong(); 
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("Token JWT inválido ou expirado!");
        }
    }

    private Instant gerarDataExpiracao() {
        // MÁGICA DA NUVEM: Forçamos o relógio a usar a hora exata do Brasil, 
        // ignorando o fuso horário físico do servidor do Render.
        return ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))
                .plusHours(2)
                .toInstant();
    }
}