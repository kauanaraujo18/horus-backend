package com.horus.projeto.services; // Verifique o nome do seu pacote

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.horus.projeto.entities.UsuarioEntity; // Importe a sua entidade de usuário
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {

    // Essa é a chave de criptografia. Em produção (Render), nós colocaremos isso
    // nas variáveis de ambiente. Por enquanto, deixamos um valor padrão seguro.
    @Value("${api.security.token.secret:minha-chave-secreta-horus-2026}")
    private String secret;

    // 1. Método que FABRICA o Token na hora do Login
    public String gerarToken(UsuarioEntity usuario) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("horus-api")
                    // Guarda quem é o usuário (o login)
                    .withSubject(usuario.getLogin()) 
                    
                    // AQUI ESTÁ A MÁGICA MULTI-TENANT: Guardamos o ID da empresa carimbado no Token!
                    .withClaim("empresaId", usuario.getEmpresa().getId()) 
                    
                    .withExpiresAt(gerarDataExpiracao())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token jwt", exception);
        }
    }

    // 2. Método que LÊ o Token e descobre quem é o Usuário
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

    // 3. Método vital para o nosso Multi-Tenant: Extrai a Empresa do Token!
    public Long getEmpresaId(String tokenJWT) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("horus-api")
                    .build()
                    .verify(tokenJWT)
                    .getClaim("empresaId").asLong(); // Lê aquele "carimbo" que colocamos lá em cima
        } catch (JWTVerificationException exception) {
            throw new RuntimeException("Token JWT inválido ou expirado!");
        }
    }

    private Instant gerarDataExpiracao() {
        // O token vale por 2 horas (padrão de mercado para segurança)
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }
}