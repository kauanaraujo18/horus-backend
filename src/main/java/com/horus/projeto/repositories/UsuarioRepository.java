package com.horus.projeto.repositories;

import com.horus.projeto.entities.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    // Método mágico do Spring para buscar pelo login
    Optional<UsuarioEntity> findByLogin(String login);
}