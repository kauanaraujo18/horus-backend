/**
 * HORUS ADMIN — Painel Master
 * Autenticação restrita a ROLE_MASTER; CRUD de empresas, usuários,
 * matriz de permissões e upload de logomarca por empresa.
 */
const API_URL = window.location.origin.startsWith('http') ? window.location.origin : 'http://localhost:8080';

const MODULOS = [
    { chave: 'PRODUTO',     rotulo: 'Produtos',        icone: 'ph-package' },
    { chave: 'VENDA',       rotulo: 'Vendas (PDV)',    icone: 'ph-shopping-cart' },
    { chave: 'PRODUCAO',    rotulo: 'Produção',        icone: 'ph-flask' },
    { chave: 'CONTA_PAGAR', rotulo: 'Contas a Pagar',  icone: 'ph-invoice' },
    { chave: 'RELATORIO',   rotulo: 'Relatórios',      icone: 'ph-chart-bar' },
    { chave: 'ETIQUETA',    rotulo: 'Etiquetas',       icone: 'ph-tag' },
    { chave: 'DASHBOARD',   rotulo: 'Dashboard',       icone: 'ph-squares-four' },
    { chave: 'USUARIO',     rotulo: 'Usuários',        icone: 'ph-users-three' }
];
const ACOES = ['CRIAR', 'VER', 'EDITAR', 'EXCLUIR'];

let token = sessionStorage.getItem('horus_admin_token') || null;
let empresasCache = [];
let usuariosCache = [];
let usuarioPermissoesAlvo = null;
let arquivoLogoSelecionado = null;
let empresaLogoAlvo = null;

/* ── Helpers ──────────────────────────────────────────────────────────── */
const $ = id => document.getElementById(id);

function authHeader() { return { 'Authorization': `Bearer ${token}` }; }

async function api(caminho, opts = {}) {
    const res = await fetch(`${API_URL}${caminho}`, {
        ...opts,
        headers: { ...(opts.headers || {}), ...authHeader() }
    });
    if (res.status === 401 || res.status === 403) {
        if (opts.silencioso401) return res;
        mostrarToast('Sessão expirada ou acesso negado. Faça login novamente.', 'error');
        fazerLogout();
        throw new Error('unauthorized');
    }
    return res;
}

function mostrarToast(msg, tipo = 'success') {
    const icones = { success: 'ph-check-circle', error: 'ph-x-circle', warning: 'ph-warning' };
    const div = document.createElement('div');
    div.className = `toast ${tipo}`;
    div.innerHTML = `<i class="ph ${icones[tipo] || icones.success}"></i> ${msg}`;
    $('toastContainer').appendChild(div);
    setTimeout(() => div.remove(), 4200);
}

function esc(txt) {
    return String(txt ?? '').replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function abrirModal(id)  { $(id).classList.add('open'); }
function fecharModal(id) { $(id).classList.remove('open'); }

/* ── Login / Logout ───────────────────────────────────────────────────── */
async function realizarLogin(e) {
    e.preventDefault();
    const btn = $('btnLoginAdmin');
    const erroBox = $('loginErro');
    erroBox.style.display = 'none';
    btn.disabled = true;
    btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Autenticando...';

    try {
        const res = await fetch(`${API_URL}/api/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ login: $('adminLogin').value.trim(), senha: $('adminSenha').value })
        });
        if (!res.ok) throw new Error('Usuário ou senha incorretos.');
        const dados = await res.json();

        if ((dados.perfil || '').toLowerCase() !== 'master') {
            throw new Error('Acesso negado: este painel é exclusivo de usuários master.');
        }

        token = dados.token;
        sessionStorage.setItem('horus_admin_token', token);
        sessionStorage.setItem('horus_admin_nome', dados.nome || 'Master');
        $('nomeMaster').innerText = dados.nome || 'Master';
        $('loginOverlay').classList.add('unlocked');
        await carregarTudo();
    } catch (err) {
        erroBox.innerText = err.message;
        erroBox.style.display = 'block';
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="ph ph-lock-key-open"></i> Entrar no painel';
    }
}

function fazerLogout() {
    sessionStorage.removeItem('horus_admin_token');
    sessionStorage.removeItem('horus_admin_nome');
    token = null;
    $('loginOverlay').classList.remove('unlocked');
}

/* ── Navegação ────────────────────────────────────────────────────────── */
const TITULOS = { dashboard: 'Visão Geral', empresas: 'Empresas', usuarios: 'Usuários & Permissões' };

function navegar(view) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.querySelectorAll('.admin-nav-btn[data-view]').forEach(b => b.classList.remove('active'));
    $(`view-${view}`).classList.add('active');
    document.querySelector(`.admin-nav-btn[data-view="${view}"]`).classList.add('active');
    $('topbarTitle').innerText = TITULOS[view];
}

/* ── Carga inicial ────────────────────────────────────────────────────── */
async function carregarTudo() {
    await Promise.all([carregarStats(), carregarEmpresas()]);
    await carregarUsuarios();
}

async function carregarStats() {
    try {
        const res = await api('/api/admin/stats');
        if (!res.ok) return;
        const s = await res.json();
        $('kpiEmpresas').innerText = s.totalEmpresas;
        $('kpiAtivas').innerText   = s.empresasAtivas;
        $('kpiUsuarios').innerText = s.totalUsuarios;
        $('kpiProdutos').innerText = s.totalProdutos;
        $('kpiVendas').innerText   = s.totalVendas;
    } catch (e) { /* sessão tratada no api() */ }
}

/* ── Empresas ─────────────────────────────────────────────────────────── */
async function carregarEmpresas() {
    const res = await api('/api/admin/empresas');
    if (!res.ok) return;
    empresasCache = await res.json();
    renderizarEmpresas();
    renderizarDashEmpresas();
    preencherSelectsEmpresa();
}

function renderizarDashEmpresas() {
    const corpo = $('tbodyDashEmpresas');
    const recentes = [...empresasCache].sort((a, b) => (b.id || 0) - (a.id || 0)).slice(0, 6);
    if (recentes.length === 0) {
        corpo.innerHTML = '<tr class="empty-row"><td colspan="4">Nenhuma empresa cadastrada.</td></tr>';
        return;
    }
    corpo.innerHTML = recentes.map(e => `
        <tr>
            <td><strong>${esc(e.razaoSocial)}</strong>${e.nomeFantasia ? `<br><span style="color:var(--text-muted);font-size:12px;">${esc(e.nomeFantasia)}</span>` : ''}</td>
            <td>${esc(e.cnpj || '—')}</td>
            <td>${esc(e.nomeProprietario || '—')}</td>
            <td class="align-center">${e.ativo ? '<span class="badge ativo">Ativa</span>' : '<span class="badge inativo">Inativa</span>'}</td>
        </tr>`).join('');
}

function renderizarEmpresas() {
    const filtro = ($('filtroEmpresas').value || '').toLowerCase();
    const lista = empresasCache.filter(e =>
        !filtro ||
        (e.razaoSocial || '').toLowerCase().includes(filtro) ||
        (e.nomeFantasia || '').toLowerCase().includes(filtro) ||
        (e.cnpj || '').toLowerCase().includes(filtro));

    const corpo = $('tbodyEmpresas');
    if (lista.length === 0) {
        corpo.innerHTML = '<tr class="empty-row"><td colspan="6">Nenhuma empresa encontrada.</td></tr>';
        return;
    }
    corpo.innerHTML = lista.map(e => `
        <tr>
            <td class="align-center" id="logoCell-${e.id}">
                ${e.possuiLogo ? `<img class="logo-thumb" data-logo-id="${e.id}" alt="logo">` : '<span class="logo-placeholder"><i class="ph ph-image"></i></span>'}
            </td>
            <td><strong>${esc(e.razaoSocial)}</strong>${e.nomeFantasia ? `<br><span style="color:var(--text-muted);font-size:12px;">${esc(e.nomeFantasia)}</span>` : ''}</td>
            <td>${esc(e.cnpj || '—')}</td>
            <td>${esc(e.nomeProprietario || '—')}<br><span style="color:var(--text-muted);font-size:12px;">${esc(e.emailProprietario || '')}</span></td>
            <td class="align-center">${e.ativo ? '<span class="badge ativo">Ativa</span>' : '<span class="badge inativo">Inativa</span>'}</td>
            <td>
                <div class="acoes-cell">
                    <button class="btn-action brand" onclick="abrirModalEmpresa(${e.id})"><i class="ph ph-pencil-simple"></i> Editar</button>
                    <button class="btn-action" onclick="abrirModalLogo(${e.id})"><i class="ph ph-image"></i> Logo</button>
                    <button class="btn-action ${e.ativo ? 'danger' : 'ok'}" onclick="alternarEmpresa(${e.id})">
                        <i class="ph ${e.ativo ? 'ph-prohibit' : 'ph-check'}"></i> ${e.ativo ? 'Desativar' : 'Ativar'}
                    </button>
                </div>
            </td>
        </tr>`).join('');

    // Carrega miniaturas autenticadas (img não envia Bearer; usamos blob)
    corpo.querySelectorAll('img[data-logo-id]').forEach(img => carregarLogoThumb(img));
}

async function carregarLogoThumb(img) {
    try {
        const res = await api(`/api/admin/empresas/${img.dataset.logoId}/logo`);
        if (res.ok) img.src = URL.createObjectURL(await res.blob());
    } catch (e) { /* ignora thumb */ }
}

function abrirModalEmpresa(id = null) {
    const e = id ? empresasCache.find(x => x.id === id) : null;
    $('tituloModalEmpresa').innerText = e ? `Editar Empresa #${e.id}` : 'Nova Empresa';
    $('empId').value           = e?.id || '';
    $('empRazao').value        = e?.razaoSocial || '';
    $('empFantasia').value     = e?.nomeFantasia || '';
    $('empCnpj').value         = e?.cnpj || '';
    $('empProprietario').value = e?.nomeProprietario || '';
    $('empCpf').value          = e?.cpfProprietario || '';
    $('empTelefone').value     = e?.telefoneProprietario || '';
    $('empEmail').value        = e?.emailProprietario || '';
    abrirModal('modalEmpresa');
}

async function salvarEmpresa() {
    const payload = {
        id: $('empId').value ? Number($('empId').value) : null,
        razaoSocial: $('empRazao').value.trim(),
        nomeFantasia: $('empFantasia').value.trim() || null,
        cnpj: $('empCnpj').value.trim() || null,
        nomeProprietario: $('empProprietario').value.trim() || null,
        cpfProprietario: $('empCpf').value.trim() || null,
        telefoneProprietario: $('empTelefone').value.trim() || null,
        emailProprietario: $('empEmail').value.trim() || null
    };
    if (!payload.razaoSocial) { mostrarToast('Informe a Razão Social.', 'warning'); return; }

    const res = await api('/api/admin/empresas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    const corpo = await res.json();
    if (res.ok) {
        mostrarToast(`Empresa ${payload.id ? 'atualizada' : 'criada'} com sucesso!`);
        fecharModal('modalEmpresa');
        await carregarEmpresas();
        carregarStats();
    } else {
        mostrarToast(corpo.erro || 'Erro ao salvar empresa.', 'error');
    }
}

async function alternarEmpresa(id) {
    const res = await api(`/api/admin/empresas/${id}/ativo`, { method: 'PATCH' });
    if (res.ok) {
        mostrarToast('Status da empresa atualizado.');
        await carregarEmpresas();
        carregarStats();
    } else {
        mostrarToast('Erro ao alterar o status.', 'error');
    }
}

/* ── Logo ─────────────────────────────────────────────────────────────── */
function abrirModalLogo(empresaId) {
    empresaLogoAlvo = empresaId;
    arquivoLogoSelecionado = null;
    $('btnEnviarLogo').disabled = true;
    const empresa = empresasCache.find(e => e.id === empresaId);
    $('tituloModalLogo').innerText = `Logomarca — ${empresa?.nomeFantasia || empresa?.razaoSocial || ''}`;
    $('logoPreviewArea').style.display = 'none';
    $('btnRemoverLogo').style.display = empresa?.possuiLogo ? 'inline-flex' : 'none';

    if (empresa?.possuiLogo) {
        api(`/api/admin/empresas/${empresaId}/logo`).then(async res => {
            if (res.ok) {
                $('logoPreviewImg').src = URL.createObjectURL(await res.blob());
                $('logoPreviewArea').style.display = 'block';
            }
        });
    }
    abrirModal('modalLogo');
}

function selecionarArquivoLogo(file) {
    if (!file) return;
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
        mostrarToast('Envie uma imagem PNG ou JPEG.', 'warning'); return;
    }
    if (file.size > 1024 * 1024) {
        mostrarToast('A logo deve ter no máximo 1 MB.', 'warning'); return;
    }
    arquivoLogoSelecionado = file;
    $('logoPreviewImg').src = URL.createObjectURL(file);
    $('logoPreviewArea').style.display = 'block';
    $('btnEnviarLogo').disabled = false;
}

async function enviarLogo() {
    if (!arquivoLogoSelecionado || !empresaLogoAlvo) return;
    const fd = new FormData();
    fd.append('arquivo', arquivoLogoSelecionado);
    const res = await api(`/api/admin/empresas/${empresaLogoAlvo}/logo`, { method: 'POST', body: fd });
    const corpo = await res.json();
    if (res.ok) {
        mostrarToast('Logo enviada! Já será usada nas próximas etiquetas.');
        fecharModal('modalLogo');
        await carregarEmpresas();
    } else {
        mostrarToast(corpo.erro || 'Erro ao enviar logo.', 'error');
    }
}

async function removerLogo() {
    if (!empresaLogoAlvo) return;
    const res = await api(`/api/admin/empresas/${empresaLogoAlvo}/logo`, { method: 'DELETE' });
    if (res.ok) {
        mostrarToast('Logo removida. As etiquetas voltarão à logo padrão.');
        fecharModal('modalLogo');
        await carregarEmpresas();
    } else {
        mostrarToast('Erro ao remover a logo.', 'error');
    }
}

/* ── Usuários ─────────────────────────────────────────────────────────── */
function preencherSelectsEmpresa() {
    const opcoes = empresasCache.map(e => `<option value="${e.id}">${esc(e.nomeFantasia || e.razaoSocial)}</option>`).join('');
    $('filtroEmpresaUsuarios').innerHTML = '<option value="">Todas as empresas</option>' + opcoes;
    $('usuEmpresa').innerHTML = opcoes;
}

async function carregarUsuarios() {
    const empresaId = $('filtroEmpresaUsuarios').value;
    const res = await api(`/api/admin/usuarios${empresaId ? `?empresaId=${empresaId}` : ''}`);
    if (!res.ok) return;
    usuariosCache = await res.json();
    renderizarUsuarios();
}

function renderizarUsuarios() {
    const filtro = ($('filtroUsuarios').value || '').toLowerCase();
    const lista = usuariosCache.filter(u =>
        !filtro || (u.nome || '').toLowerCase().includes(filtro) || (u.login || '').toLowerCase().includes(filtro));

    const corpo = $('tbodyUsuarios');
    if (lista.length === 0) {
        corpo.innerHTML = '<tr class="empty-row"><td colspan="7">Nenhum usuário encontrado.</td></tr>';
        return;
    }
    corpo.innerHTML = lista.map(u => `
        <tr>
            <td><strong>${esc(u.nome)}</strong></td>
            <td>${esc(u.login)}</td>
            <td>${esc(u.empresaNome || '—')}</td>
            <td class="align-center"><span class="badge ${esc(u.perfil || 'operador')}">${esc(u.perfil || '—')}</span></td>
            <td class="align-center">
                <button class="btn-action brand" onclick="abrirModalPermissoes(${u.id})">
                    <i class="ph ph-shield-check"></i> ${ (u.permissoes || []).length }/${MODULOS.length * ACOES.length}
                </button>
            </td>
            <td class="align-center">${u.ativo ? '<span class="badge ativo">Ativo</span>' : '<span class="badge inativo">Inativo</span>'}</td>
            <td>
                <div class="acoes-cell">
                    <button class="btn-action" onclick="abrirModalUsuario(${u.id})"><i class="ph ph-pencil-simple"></i></button>
                    <button class="btn-action warn" onclick="resetarSenhaUsuario(${u.id})" title="Resetar senha"><i class="ph ph-key"></i></button>
                    <button class="btn-action ${u.ativo ? 'danger' : 'ok'}" onclick="alternarUsuario(${u.id})" title="${u.ativo ? 'Desativar' : 'Ativar'}">
                        <i class="ph ${u.ativo ? 'ph-prohibit' : 'ph-check'}"></i>
                    </button>
                </div>
            </td>
        </tr>`).join('');
}

function abrirModalUsuario(id = null) {
    const u = id ? usuariosCache.find(x => x.id === id) : null;
    $('tituloModalUsuario').innerText = u ? `Editar Usuário — ${u.nome}` : 'Novo Usuário';
    $('usuId').value    = u?.id || '';
    $('usuNome').value  = u?.nome || '';
    $('usuLogin').value = u?.login || '';
    $('usuLogin').disabled = !!u;
    $('usuSenha').value = '';
    $('grupoSenha').style.display = u ? 'none' : 'flex';
    $('usuPerfil').value = u?.perfil || 'operador';
    if (u?.empresaId) $('usuEmpresa').value = u.empresaId;
    abrirModal('modalUsuario');
}

async function salvarUsuario() {
    const id = $('usuId').value;
    const corpoReq = id
        ? { nome: $('usuNome').value.trim(), perfil: $('usuPerfil').value, empresaId: Number($('usuEmpresa').value) }
        : {
            nome: $('usuNome').value.trim(),
            login: $('usuLogin').value.trim(),
            senha: $('usuSenha').value,
            perfil: $('usuPerfil').value,
            empresaId: Number($('usuEmpresa').value)
          };

    if (!corpoReq.nome) { mostrarToast('Informe o nome.', 'warning'); return; }
    if (!id && (!corpoReq.login || !corpoReq.senha || corpoReq.senha.length < 8)) {
        mostrarToast('Login e senha (mín. 8 caracteres) são obrigatórios.', 'warning'); return;
    }

    const res = await api(id ? `/api/admin/usuarios/${id}` : '/api/admin/usuarios', {
        method: id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(corpoReq)
    });
    const corpo = await res.json();
    if (res.ok) {
        mostrarToast(`Usuário ${id ? 'atualizado' : 'criado'} com sucesso!`);
        fecharModal('modalUsuario');
        await carregarUsuarios();
        carregarStats();
    } else {
        mostrarToast(corpo.erro || 'Erro ao salvar usuário.', 'error');
    }
}

async function alternarUsuario(id) {
    const res = await api(`/api/admin/usuarios/${id}/ativo`, { method: 'PATCH' });
    const corpo = await res.json();
    if (res.ok) {
        mostrarToast('Status do usuário atualizado.');
        await carregarUsuarios();
    } else {
        mostrarToast(corpo.erro || 'Erro ao alterar o status.', 'error');
    }
}

async function resetarSenhaUsuario(id) {
    const u = usuariosCache.find(x => x.id === id);
    if (!confirm(`Gerar nova senha aleatória para "${u?.nome}"?\nA senha atual deixará de funcionar imediatamente.`)) return;
    const res = await api(`/api/admin/usuarios/${id}/reset-senha`, { method: 'POST' });
    const corpo = await res.json();
    if (res.ok) {
        prompt('Senha resetada! Copie e entregue ao usuário (exibida apenas uma vez):', corpo.novaSenha);
    } else {
        mostrarToast(corpo.erro || 'Erro ao resetar senha.', 'error');
    }
}

/* ── Matriz de Permissões ─────────────────────────────────────────────── */
async function abrirModalPermissoes(usuarioId) {
    usuarioPermissoesAlvo = usuarioId;
    const u = usuariosCache.find(x => x.id === usuarioId);
    $('tituloModalPermissoes').innerText = `Permissões — ${u?.nome || ''}`;

    const res = await api(`/api/admin/usuarios/${usuarioId}/permissoes`);
    const ativas = res.ok ? new Set(await res.json()) : new Set();

    $('tbodyPermissoes').innerHTML = MODULOS.map(m => `
        <tr data-modulo="${m.chave}">
            <td><span class="perm-modulo-icon"><i class="ph ${m.icone}"></i></span>${m.rotulo}</td>
            ${ACOES.map(a => `
                <td><input type="checkbox" class="perm-check" data-perm="${m.chave}_${a}"
                    ${ativas.has(`${m.chave}_${a}`) ? 'checked' : ''}></td>`).join('')}
            <td><input type="checkbox" class="perm-linha-toda" title="Marcar/desmarcar linha"></td>
        </tr>`).join('');

    // Sincroniza o checkbox "Todos" de cada linha
    document.querySelectorAll('#tbodyPermissoes tr').forEach(tr => {
        const checks = tr.querySelectorAll('.perm-check');
        const todas = tr.querySelector('.perm-linha-toda');
        const sincronizar = () => { todas.checked = [...checks].every(c => c.checked); };
        sincronizar();
        checks.forEach(c => c.addEventListener('change', sincronizar));
        todas.addEventListener('change', () => {
            checks.forEach(c => { c.checked = todas.checked; });
        });
    });

    abrirModal('modalPermissoes');
}

async function salvarPermissoes() {
    if (!usuarioPermissoesAlvo) return;
    const selecionadas = [...document.querySelectorAll('.perm-check:checked')].map(c => c.dataset.perm);
    const res = await api(`/api/admin/usuarios/${usuarioPermissoesAlvo}/permissoes`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ permissoes: selecionadas })
    });
    const corpo = await res.json();
    if (res.ok) {
        mostrarToast('Permissões salvas com sucesso!');
        fecharModal('modalPermissoes');
        await carregarUsuarios();
    } else {
        mostrarToast(corpo.erro || 'Erro ao salvar permissões.', 'error');
    }
}

/* ── Bootstrap ────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    $('formLoginAdmin').addEventListener('submit', realizarLogin);
    $('btnLogout').addEventListener('click', fazerLogout);

    document.querySelectorAll('.admin-nav-btn[data-view]').forEach(btn =>
        btn.addEventListener('click', () => navegar(btn.dataset.view)));

    document.querySelectorAll('[data-close]').forEach(btn =>
        btn.addEventListener('click', () => fecharModal(btn.dataset.close)));

    // Empresas
    $('btnNovaEmpresa').addEventListener('click', () => abrirModalEmpresa());
    $('btnSalvarEmpresa').addEventListener('click', salvarEmpresa);
    $('filtroEmpresas').addEventListener('input', renderizarEmpresas);

    // Logo (clique + drag and drop)
    const drop = $('logoDrop');
    drop.addEventListener('click', () => $('logoFile').click());
    $('logoFile').addEventListener('change', e => selecionarArquivoLogo(e.target.files[0]));
    drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('drag'); });
    drop.addEventListener('dragleave', () => drop.classList.remove('drag'));
    drop.addEventListener('drop', e => {
        e.preventDefault(); drop.classList.remove('drag');
        selecionarArquivoLogo(e.dataTransfer.files[0]);
    });
    $('btnEnviarLogo').addEventListener('click', enviarLogo);
    $('btnRemoverLogo').addEventListener('click', removerLogo);

    // Usuários
    $('btnNovoUsuario').addEventListener('click', () => abrirModalUsuario());
    $('btnSalvarUsuario').addEventListener('click', salvarUsuario);
    $('filtroUsuarios').addEventListener('input', renderizarUsuarios);
    $('filtroEmpresaUsuarios').addEventListener('change', carregarUsuarios);

    // Permissões
    $('btnSalvarPermissoes').addEventListener('click', salvarPermissoes);
    $('btnPermTodas').addEventListener('click', () =>
        document.querySelectorAll('.perm-check, .perm-linha-toda').forEach(c => { c.checked = true; }));
    $('btnPermNenhuma').addEventListener('click', () =>
        document.querySelectorAll('.perm-check, .perm-linha-toda').forEach(c => { c.checked = false; }));

    // Sessão existente: valida o token contra um endpoint master antes de liberar
    if (token) {
        api('/api/admin/stats', { silencioso401: true }).then(async res => {
            if (res.ok) {
                $('nomeMaster').innerText = sessionStorage.getItem('horus_admin_nome') || 'Master';
                $('loginOverlay').classList.add('unlocked');
                await carregarTudo();
            } else {
                fazerLogout();
            }
        }).catch(() => fazerLogout());
    }
});
