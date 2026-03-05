package com.horus.projeto.services;

import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Método para validar o login
     * Retorna o Usuário se der certo, ou lança erro se falhar.
     */
    public UsuarioEntity autenticar(String login, String senha) {
        
        // 1. Busca o usuário no banco pelo login
        Optional<UsuarioEntity> usuarioOpt = usuarioRepository.findByLogin(login);

        // 2. Verifica se existe
        if (usuarioOpt.isEmpty()) {
            throw new RuntimeException("Usuário não encontrado.");
        }

        UsuarioEntity usuario = usuarioOpt.get();

        // 3. Verifica se a senha bate (Comparação simples por enquanto)
        // Obs: Em produção, usaríamos BCrypt ou Argon2 aqui.
        if (!usuario.getSenha().equals(senha)) {
            throw new RuntimeException("Senha incorreta.");
        }

        // 4. Verifica se está ativo
        if (!usuario.getAtivo()) {
            throw new RuntimeException("Usuário inativo. Contate o administrador.");
        }

        return usuario;
    }
}