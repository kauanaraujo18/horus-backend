package com.horus.projeto.config; // Verifique o nome do seu pacote

import com.horus.projeto.repositories.UsuarioRepository;
import com.horus.projeto.services.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        System.out.println("--- INICIANDO SECURITY FILTER ---");
        System.out.println("Endpoint chamado: " + request.getRequestURI());

        var tokenJWT = recuperarToken(request);
        System.out.println("Token recuperado: " + tokenJWT);

        if (tokenJWT != null) {
            try {
                var login = tokenService.getSubject(tokenJWT);
                System.out.println("Login extraído do token: " + login);
                
                var usuario = usuarioRepository.findByLogin(login).orElse(null);
                System.out.println("Usuário encontrado no banco? " + (usuario != null));

                if (usuario != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("AUTENTICAÇÃO LIBERADA COM SUCESSO!");
                }
            } catch (Exception e) {
                System.out.println("ERRO AO PROCESSAR O TOKEN: " + e.getMessage());
            }
        } else {
            System.out.println("NENHUM TOKEN FOI ENCONTRADO NA REQUISIÇÃO!");
        }

        System.out.println("--- FINALIZANDO SECURITY FILTER ---");
        filterChain.doFilter(request, response);
    }

    private String recuperarToken(HttpServletRequest request) {
        var authorizationHeader = request.getHeader("Authorization");
        System.out.println("Cabeçalho Authorization original: " + authorizationHeader);
        
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.replace("Bearer ", "");
        }
        return null;
    }
}