package com.horus.projeto.config;

import com.horus.projeto.entities.EmpresaEntity;
import com.horus.projeto.entities.PermissaoEntity;
import com.horus.projeto.entities.UsuarioEntity;
import com.horus.projeto.repositories.EmpresaRepository;
import com.horus.projeto.repositories.PermissaoRepository;
import com.horus.projeto.repositories.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Garante na inicialização:
 *  1. O catálogo completo de permissões CRUD por módulo.
 *  2. O usuário master do painel admin — criado SOMENTE se as env vars
 *     ADMIN_MASTER_LOGIN e ADMIN_MASTER_SENHA estiverem definidas
 *     (nunca cria credencial padrão hardcoded).
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    public static final String[] MODULOS = {
            "PRODUTO", "VENDA", "PRODUCAO", "CONTA_PAGAR",
            "RELATORIO", "ETIQUETA", "DASHBOARD", "USUARIO"
    };
    public static final String[] ACOES = { "CRIAR", "VER", "EDITAR", "EXCLUIR" };

    private final PermissaoRepository permissaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.master.login:}")
    private String masterLogin;

    @Value("${admin.master.senha:}")
    private String masterSenha;

    public AdminSeeder(PermissaoRepository permissaoRepository,
                       UsuarioRepository usuarioRepository,
                       EmpresaRepository empresaRepository,
                       PasswordEncoder passwordEncoder) {
        this.permissaoRepository = permissaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedPermissoes();
        seedUsuarioMaster();
    }

    private void seedPermissoes() {
        int criadas = 0;
        for (String modulo : MODULOS) {
            for (String acao : ACOES) {
                String nome = modulo + "_" + acao;
                if (permissaoRepository.findByNome(nome).isEmpty()) {
                    permissaoRepository.save(new PermissaoEntity(null, nome,
                            "Permite " + acao.toLowerCase() + " em " + modulo.replace('_', ' ').toLowerCase()));
                    criadas++;
                }
            }
        }
        if (criadas > 0) log.info("Catálogo de permissões: {} novas permissões criadas.", criadas);
    }

    private void seedUsuarioMaster() {
        if (masterLogin == null || masterLogin.isBlank() || masterSenha == null || masterSenha.isBlank()) {
            log.info("ADMIN_MASTER_LOGIN/ADMIN_MASTER_SENHA não definidos — seed do usuário master ignorado.");
            return;
        }
        if (masterSenha.length() < 10) {
            log.warn("ADMIN_MASTER_SENHA muito curta (mínimo 10 caracteres) — usuário master NÃO criado.");
            return;
        }
        if (usuarioRepository.findByLogin(masterLogin).isPresent()) {
            return; // já existe — nunca sobrescreve a senha
        }

        EmpresaEntity empresaAdmin = empresaRepository.findAll().stream()
                .filter(e -> "HORUS ADMINISTRACAO".equalsIgnoreCase(e.getRazaoSocial()))
                .findFirst()
                .orElseGet(() -> {
                    EmpresaEntity e = new EmpresaEntity();
                    e.setRazaoSocial("HORUS ADMINISTRACAO");
                    e.setNomeFantasia("Horus Admin");
                    e.setAtivo(true);
                    return empresaRepository.save(e);
                });

        UsuarioEntity master = new UsuarioEntity();
        master.setNome("Master Horus");
        master.setLogin(masterLogin);
        master.setSenha(passwordEncoder.encode(masterSenha));
        master.setPerfil("master");
        master.setAtivo(true);
        master.setEmpresa(empresaAdmin);
        master.setPermissoes(permissaoRepository.findAll());
        usuarioRepository.save(master);
        log.info("Usuário master '{}' criado com sucesso.", masterLogin);
    }
}
