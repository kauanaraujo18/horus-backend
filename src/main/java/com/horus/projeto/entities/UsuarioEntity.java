package com.horus.projeto.entities;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "usuario")
public class UsuarioEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String senha;

    private String perfil; // ADMIN ou OPERADOR

    private Boolean ativo = true;

    @ManyToOne 
    @JoinColumn(name = "empresa_id") 
    private EmpresaEntity empresa;

    @ManyToMany(fetch = FetchType.EAGER) 
    @JoinTable(
        name = "permissao_usuario", 
        joinColumns = @JoinColumn(name = "usuario_id"), 
        inverseJoinColumns = @JoinColumn(name = "permissao_id") 
    )
    private List<PermissaoEntity> permissoes;

    // --- GETTERS E SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }

    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }

    // --- CORREÇÃO: GETTERS E SETTERS DA EMPRESA E PERMISSÕES ---
    public EmpresaEntity getEmpresa() { return empresa; }
    public void setEmpresa(EmpresaEntity empresa) { this.empresa = empresa; }

    public List<PermissaoEntity> getPermissoes() { return permissoes; }
    public void setPermissoes(List<PermissaoEntity> permissoes) { this.permissoes = permissoes; }


    // --- MÉTODOS OBRIGATÓRIOS DO SPRING SECURITY ---

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        var authorities = new java.util.ArrayList<GrantedAuthority>();

        // O perfil vira uma ROLE do Spring Security (ex: master → ROLE_MASTER)
        if (this.perfil != null && !this.perfil.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + this.perfil.trim().toUpperCase()));
        }

        // Permissões granulares (ex: PRODUTO_CRIAR, VENDA_VER)
        if (this.permissoes != null) {
            this.permissoes.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getNome())));
        }
        return authorities;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.login; 
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return ativo == null || ativo; }
}