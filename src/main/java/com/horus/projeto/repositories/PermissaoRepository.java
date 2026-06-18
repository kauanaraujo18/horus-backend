package com.horus.projeto.repositories;

import com.horus.projeto.entities.PermissaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissaoRepository extends JpaRepository<PermissaoEntity, Long> {
    Optional<PermissaoEntity> findByNome(String nome);
}
