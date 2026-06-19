package com.horus.projeto.services;

import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.PermissaoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> PERFIS_VALIDOS = Set.of("master", "admin", "operador");
    private static final Set<String> TIPOS_LOGO = Set.of("image/png", "image/jpeg", "image/jpg");
    private static final long TAMANHO_MAX_LOGO = 1024 * 1024; // 1 MB
    private static final String SENHA_PADRAO_INICIAL = "Mudar@123"; // mesma do cadastro publico

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermissaoRepository permissaoRepository;
    private final ProdutoRepository produtoRepository;
    private final VendaRepository vendaRepository;
    private final PasswordEncoder passwordEncoder;

    /* ── Dashboard ──────────────────────────────────────────────────────── */

    public Map<String, Object> stats() {
        List<EmpresaEntity> empresas = empresaRepository.findAll();
        long ativas = empresas.stream().filter(e -> Boolean.TRUE.equals(e.getAtivo())).count();
        return Map.of(
                "totalEmpresas", empresas.size(),
                "empresasAtivas", ativas,
                "empresasInativas", empresas.size() - ativas,
                "totalUsuarios", usuarioRepository.count(),
                "totalProdutos", produtoRepository.count(),
                "totalVendas", vendaRepository.count()
        );
    }

    /* ── Empresas ───────────────────────────────────────────────────────── */

    public List<EmpresaEntity> listarEmpresas() {
        return empresaRepository.findAll().stream()
                .sorted(Comparator.comparing(e -> Optional.ofNullable(e.getRazaoSocial()).orElse("")))
                .collect(Collectors.toList());
    }

    @Transactional
    public EmpresaEntity salvarEmpresa(EmpresaEntity dados) {
        if (dados.getRazaoSocial() == null || dados.getRazaoSocial().isBlank()) {
            throw new IllegalArgumentException("Razão Social é obrigatória.");
        }
        if (dados.getId() != null) {
            EmpresaEntity existente = buscarEmpresa(dados.getId());
            existente.setRazaoSocial(dados.getRazaoSocial());
            existente.setNomeFantasia(dados.getNomeFantasia());
            existente.setCnpj(dados.getCnpj());
            existente.setNomeProprietario(dados.getNomeProprietario());
            existente.setTelefoneProprietario(dados.getTelefoneProprietario());
            existente.setEmailProprietario(dados.getEmailProprietario());
            existente.setCpfProprietario(dados.getCpfProprietario());
            existente.setDataNascimentoProprietario(dados.getDataNascimentoProprietario());
            return empresaRepository.save(existente);
        }
        dados.setAtivo(true);
        EmpresaEntity salva = empresaRepository.save(dados);
        criarUsuarioInicialDaEmpresa(salva);
        return salva;
    }

    /**
     * Cria o primeiro usuário (admin) da empresa a partir do e-mail do proprietário,
     * espelhando o que a procedure pr_registrar_nova_conta faz no cadastro público.
     * A senha é gravada em TEXTO PLANO de propósito: a trigger BEFORE INSERT da tabela
     * usuario aplica o BCrypt (mesmo mecanismo do cadastro público). NÃO criptografar aqui.
     */
    private void criarUsuarioInicialDaEmpresa(EmpresaEntity empresa) {
        String login = empresa.getEmailProprietario();
        if (login == null || login.isBlank()) {
            return; // sem e-mail do proprietário não há login a definir
        }
        login = login.trim();
        if (usuarioRepository.findByLogin(login).isPresent()) {
            return; // já existe usuário com este login — não duplica
        }
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome((empresa.getNomeProprietario() != null && !empresa.getNomeProprietario().isBlank())
                ? empresa.getNomeProprietario().trim() : empresa.getRazaoSocial());
        usuario.setLogin(login);
        usuario.setSenha(SENHA_PADRAO_INICIAL); // plano; a trigger da tabela usuario aplica o BCrypt
        usuario.setPerfil("admin");
        usuario.setAtivo(true);
        usuario.setEmpresa(empresa);
        usuario.setPermissoes(new ArrayList<>());
        usuarioRepository.save(usuario);
    }

    @Transactional
    public EmpresaEntity alternarAtivoEmpresa(Long id) {
        EmpresaEntity empresa = buscarEmpresa(id);
        empresa.setAtivo(!Boolean.TRUE.equals(empresa.getAtivo()));
        return empresaRepository.save(empresa);
    }

    @Transactional
    public void uploadLogo(Long empresaId, MultipartFile arquivo) throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Nenhum arquivo enviado.");
        }
        if (!TIPOS_LOGO.contains(Optional.ofNullable(arquivo.getContentType()).orElse("").toLowerCase())) {
            throw new IllegalArgumentException("Formato inválido. Envie uma imagem PNG ou JPEG.");
        }
        if (arquivo.getSize() > TAMANHO_MAX_LOGO) {
            throw new IllegalArgumentException("A logo deve ter no máximo 1 MB.");
        }
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        empresa.setLogo(arquivo.getBytes());
        empresa.setLogoContentType(arquivo.getContentType());
        empresaRepository.save(empresa);
    }

    @Transactional
    public void removerLogo(Long empresaId) {
        EmpresaEntity empresa = buscarEmpresa(empresaId);
        empresa.setLogo(null);
        empresa.setLogoContentType(null);
        empresaRepository.save(empresa);
    }

    public EmpresaEntity buscarEmpresa(Long id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa não encontrada: " + id));
    }

    /* ── Usuários ───────────────────────────────────────────────────────── */

    public List<Map<String, Object>> listarUsuarios(Long empresaId) {
        List<UsuarioEntity> usuarios = (empresaId != null)
                ? usuarioRepository.findByEmpresaIdOrderByNomeAsc(empresaId)
                : usuarioRepository.findAllByOrderByNomeAsc();
        return usuarios.stream().map(this::usuarioParaMapa).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> criarUsuario(String nome, String login, String senha, String perfil, Long empresaId) {
        validarPerfil(perfil);
        if (nome == null || nome.isBlank() || login == null || login.isBlank()) {
            throw new IllegalArgumentException("Nome e login são obrigatórios.");
        }
        if (senha == null || senha.length() < 8) {
            throw new IllegalArgumentException("A senha deve ter no mínimo 8 caracteres.");
        }
        if (usuarioRepository.findByLogin(login.trim()).isPresent()) {
            throw new IllegalArgumentException("Já existe um usuário com este login.");
        }
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome(nome.trim());
        usuario.setLogin(login.trim());
        usuario.setSenha(senha); // texto plano: a trigger BEFORE INSERT da tabela usuario aplica o BCrypt
        usuario.setPerfil(perfil.toLowerCase());
        usuario.setAtivo(true);
        usuario.setEmpresa(buscarEmpresa(empresaId));
        usuario.setPermissoes(new ArrayList<>());
        return usuarioParaMapa(usuarioRepository.save(usuario));
    }

    @Transactional
    public Map<String, Object> atualizarUsuario(Long id, String nome, String perfil, Long empresaId, Long usuarioLogadoId) {
        validarPerfil(perfil);
        UsuarioEntity usuario = buscarUsuario(id);

        // Um master não pode rebaixar o próprio perfil (evita lock-out do painel)
        if (id.equals(usuarioLogadoId) && !"master".equalsIgnoreCase(perfil)) {
            throw new IllegalArgumentException("Você não pode remover seu próprio perfil master.");
        }

        if (nome != null && !nome.isBlank()) usuario.setNome(nome.trim());
        usuario.setPerfil(perfil.toLowerCase());
        if (empresaId != null) usuario.setEmpresa(buscarEmpresa(empresaId));
        return usuarioParaMapa(usuarioRepository.save(usuario));
    }

    @Transactional
    public Map<String, Object> alternarAtivoUsuario(Long id, Long usuarioLogadoId) {
        if (id.equals(usuarioLogadoId)) {
            throw new IllegalArgumentException("Você não pode desativar seu próprio usuário.");
        }
        UsuarioEntity usuario = buscarUsuario(id);
        usuario.setAtivo(!Boolean.TRUE.equals(usuario.getAtivo()));
        return usuarioParaMapa(usuarioRepository.save(usuario));
    }

    @Transactional
    public String resetarSenha(Long id) {
        UsuarioEntity usuario = buscarUsuario(id);
        String novaSenha = gerarSenhaAleatoria();
        // Reset é UPDATE: a trigger BEFORE INSERT da tabela usuario NÃO dispara aqui,
        // então o BCrypt precisa ser feito no Java mesmo (hash único, correto).
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        return novaSenha; // exibida uma única vez no painel
    }

    /* ── Permissões ─────────────────────────────────────────────────────── */

    public List<PermissaoEntity> catalogoPermissoes() {
        return permissaoRepository.findAll().stream()
                .sorted(Comparator.comparing(PermissaoEntity::getNome))
                .collect(Collectors.toList());
    }

    public List<String> permissoesDoUsuario(Long id) {
        return buscarUsuario(id).getPermissoes().stream()
                .map(PermissaoEntity::getNome)
                .sorted()
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> salvarPermissoesUsuario(Long id, List<String> nomesPermissoes) {
        UsuarioEntity usuario = buscarUsuario(id);
        List<PermissaoEntity> novas = new ArrayList<>();
        if (nomesPermissoes != null) {
            for (String nome : nomesPermissoes) {
                permissaoRepository.findByNome(nome).ifPresent(novas::add);
            }
        }
        usuario.setPermissoes(novas);
        return usuarioParaMapa(usuarioRepository.save(usuario));
    }

    /* ── Helpers ────────────────────────────────────────────────────────── */

    private UsuarioEntity buscarUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + id));
    }

    private void validarPerfil(String perfil) {
        if (perfil == null || !PERFIS_VALIDOS.contains(perfil.toLowerCase())) {
            throw new IllegalArgumentException("Perfil inválido. Use: master, admin ou operador.");
        }
    }

    private Map<String, Object> usuarioParaMapa(UsuarioEntity u) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", u.getId());
        mapa.put("nome", u.getNome());
        mapa.put("login", u.getLogin());
        mapa.put("perfil", u.getPerfil());
        mapa.put("ativo", u.getAtivo());
        mapa.put("empresaId", u.getEmpresa() != null ? u.getEmpresa().getId() : null);
        mapa.put("empresaNome", u.getEmpresa() != null ? u.getEmpresa().getNome() : null);
        mapa.put("permissoes", u.getPermissoes() == null ? List.of()
                : u.getPermissoes().stream().map(PermissaoEntity::getNome).sorted().toList());
        return mapa;
    }

    private String gerarSenhaAleatoria() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}
