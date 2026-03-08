package com.horus.projeto.services; // Lembre-se de confirmar o nome da sua pasta de serviços

import com.horus.projeto.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UsuarioRepository repository;

    // Esse método é acionado automaticamente pelo Spring Security na hora do login
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        // Aqui nós usamos o seu método Optional brilhantemente! 
        // Se ele achar o usuário, ele retorna. Se não achar, ele lança o erro padrão de segurança.
        return repository.findByLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));
    }
}