package com.horus.projeto.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfigurations {

    @Autowired
    private SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()) // Desabilita proteção contra ataques que não se aplicam a APIs REST
                // Avisa ao Spring que a nossa API é STATELESS (não guarda sessão, cada requisição precisa do Token)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> {
                    // Libera o acesso público apenas para a rota de Login
                    req.requestMatchers(HttpMethod.POST, "/api/usuarios/login").permitAll();
                    
                    // Libera as requisições de "pré-voo" do navegador (CORS) para o seu Frontend separado
                    req.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // Libera o visual e os scripts!
                    req.requestMatchers("/css/**", "/js/**", "/assets/**", "/img/**").permitAll();

                    // Libera as mensagens de erro do Java para chegarem no Front!
                    req.requestMatchers("/error").permitAll();

                    // Libera todos os arquivos visuais do seu Frontend!
                    req.requestMatchers("/*.html").permitAll(); 
                    req.requestMatchers("/").permitAll(); // Libera a raiz do site
                    
                    // Exige que TODAS as outras rotas (produtos, vendas, etc) precisem de autenticação
                    req.anyRequest().authenticated();
                })
                // Coloca o nosso SecurityFilter ANTES do filtro padrão do Spring
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // Ensina o Spring Security a injetar o Gerenciador de Autenticação no nosso Controller
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // Configura o algoritmo de hash de senhas (BCrypt). O Hórus não guardará senhas em texto puro!
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}