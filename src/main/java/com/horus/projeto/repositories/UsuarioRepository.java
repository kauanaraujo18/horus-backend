package com.horus.projeto.repositories;

import com.horus.projeto.entities.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    
    // Método mágico do Spring para buscar pelo login
    Optional<UsuarioEntity> findByLogin(String login);

    java.util.List<UsuarioEntity> findByEmpresaIdOrderByNomeAsc(Long empresaId);

    java.util.List<UsuarioEntity> findAllByOrderByNomeAsc();

    // Conexão direta e transacional com a Procedure do PostgreSQL
    @Modifying
    @Transactional
    @Query(value = "CALL public.pr_registrar_nova_conta(:razaoSocial, :nomeFantasia, :cnpj, :nomeProprietario, :telefone, :email, :cpf, CAST(:dataNascimento AS DATE), :senha)", nativeQuery = true)
    void registrarNovaConta(
        @Param("razaoSocial") String razaoSocial,
        @Param("nomeFantasia") String nomeFantasia,
        @Param("cnpj") String cnpj,
        @Param("nomeProprietario") String nomeProprietario,
        @Param("telefone") String telefone,
        @Param("email") String email,
        @Param("cpf") String cpf,
        @Param("dataNascimento") String dataNascimento,
        @Param("senha") String senha
    );
}