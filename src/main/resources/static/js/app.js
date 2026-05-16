/**
 * HORUS - Sistema de Gestão Premium
 * Arquitetura Front-end (Monolithic JS App)
 * Integração com Backend Spring Boot (REST API)
 */

//const { set } = require("express/lib/application");

/* ==========================================================================
   CONFIGURAÇÕES GLOBAIS
   ========================================================================== */
const API_URL = "https://horus-api-cjb4.onrender.com";
//const API_URL = "http://localhost:8080";
let zIndexCounter = 100; // Controle de profundidade das janelas
let cascadeOffset = 0;   // Controle de posição em cascata

// Estado Global
let usuarioLogado = null;
let carrinhoPDV = [];
let origemPesquisaSpotlight = '';

/* ==========================================================================
   INICIALIZAÇÃO (BOOTSTRAP)
   ========================================================================== */
document.addEventListener('DOMContentLoaded', () => {
    console.log("Inicializando Horus Workspace...");

    // 1. Setup de Segurança (Gatekeeper)
    setupLoginSystem();

    // 2. Setup do Window Management (Workspace)
    setupWindowManagement();

    // 3. Setup dos Módulos de Negócio
    setupProdutosModule();
    setupPDVModule();
    setupRelatoriosModule();
    setupDashboardModule();
    setupSpotlightSearch();
    setupFormatters();
});

/* ==========================================================================
   MÓDULO 1: GATEKEEPER & AUTENTICAÇÃO
   ========================================================================== */
function setupLoginSystem() {
    carregarIdentificacaoSessao();
    const loginOverlay = document.getElementById('login-overlay');
    const formLogin = document.getElementById('formLogin');
    const btnLogout = document.querySelector('.btn-logout');

    // Força o bloqueio inicial (se não houver token válido guardado)
    if (loginOverlay && !localStorage.getItem('tokenHorus')) {
        loginOverlay.style.display = 'flex';
    } else if (loginOverlay) {
        // Se já tem token, remove o overlay suavemente
        loginOverlay.classList.add('unlocked');
        setTimeout(() => loginOverlay.style.display = 'none', 600);
    }

    if (btnLogout) {
        btnLogout.addEventListener('click', () => {
            localStorage.removeItem('tokenHorus');
            // Limpa também os nomes ao sair:
            localStorage.removeItem('horus_usuario_nome');
            localStorage.removeItem('horus_empresa_nome');
            window.location.reload();
        });
    }
}

async function realizarLoginVisual() {
    const loginInput = document.getElementById('loginUsuario').value;
    const senhaInput = document.getElementById('loginSenha').value;
    const btnEntrar = document.querySelector('.btn-login-entrar');
    const loginOverlay = document.getElementById('login-overlay');

    if (!loginInput || !senhaInput) return;

    const textoOriginal = btnEntrar.innerHTML;
    btnEntrar.disabled = true;
    btnEntrar.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Autenticando...';

    try {
        const response = await fetch(`${API_URL}/api/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ login: loginInput, senha: senhaInput })
        });

        if (response.ok) {
            const dados = await response.json();
            
            // 1. Salva o Token
            localStorage.setItem('tokenHorus', dados.token);
            
            // 2. Extrai os Nomes
            const nomeUser = dados.nome || dados.usuario || 'Operador';
            const nomeEmp = dados.empresaNome || dados.empresa?.nome || 'Horus Gestão';
            
            // ==========================================
            // O QUE FALTAVA: SALVAR E ATUALIZAR A TELA
            // ==========================================
            localStorage.setItem('horus_usuario_nome', nomeUser);
            localStorage.setItem('horus_empresa_nome', nomeEmp);
            atualizarBadgeIdentificacao(nomeEmp, nomeUser);
            // ==========================================

            // Animação de desbloqueio Premium
            if (loginOverlay) {
                loginOverlay.classList.add('unlocked');
                setTimeout(() => loginOverlay.style.display = 'none', 600);
            }
        } else {
            alert("Acesso Negado: Credenciais inválidas.");
            document.getElementById('loginSenha').value = '';
        }
    } catch (error) {
        console.error("Erro na API:", error);
        alert("Erro de conexão com o servidor. Verifique o backend.");
    } finally {
        btnEntrar.disabled = false;
        btnEntrar.innerHTML = textoOriginal;
    }
}

/* ==========================================================================
   MÓDULO 2: WINDOW MANAGEMENT (WORKSPACE)
   ========================================================================== */
function setupWindowManagement() {
    // 1. Mapeamento da Sidebar
    const navMap = {
        'produtosBtn': 'produtosDiv',
        'caixaBtn': 'caixaDiv',
        'relatoriosBtn': 'relatoriosDiv',
        'dashboardBtn': 'dashboardDiv'
    };

    for (const [btnId, winId] of Object.entries(navMap)) {
        const btn = document.getElementById(btnId);
        if (btn) {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                abrirJanela(winId);
            });
        }
    }

    // 2. Configurar Drag & Drop e Fechamento
    document.querySelectorAll('.window').forEach(win => {
        makeDraggable(win);
        win.addEventListener('mousedown', () => bringToFront(win));

        const closeBtn = win.querySelector('.close-btn');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => fecharJanela(win));
        }
    });
}

function abrirJanela(winId) {
    const win = document.getElementById(winId);
    if (!win) return;

    // Reseta views para o menu padrão ao abrir
    if (winId === 'produtosDiv') navegarProdutos('menu');
    if (winId === 'relatoriosDiv') navegarRelatorios('menu');

    win.style.display = 'flex';
    
    // Pequeno delay para a transição CSS funcionar
    setTimeout(() => {
        win.classList.add('is-visible');
        bringToFront(win);
        posicionarJanela(win);
    }, 10);
}

function fecharJanela(win) {
    win.classList.remove('is-visible');
    setTimeout(() => {
        win.style.display = 'none';
        const abertas = document.querySelectorAll('.window.is-visible');
        if (abertas.length === 0) cascadeOffset = 0;
    }, 200); // Tempo da transição CSS
}

function bringToFront(win) {
    zIndexCounter++;
    win.style.zIndex = zIndexCounter;
}

function posicionarJanela(win) {
    const winW = window.innerWidth;
    const winH = window.innerHeight;
    const width = win.offsetWidth || 600;
    const height = win.offsetHeight || 400;

    let posX = (winW / 2) - (width / 2) + (cascadeOffset * 30);
    let posY = (winH / 2) - (height / 2) + (cascadeOffset * 30);

    // Evita vazar da tela
    if (posX + width > winW || posY + height > winH) {
        cascadeOffset = 0;
        posX = (winW / 2) - (width / 2);
        posY = (winH / 2) - (height / 2);
    }

    win.style.left = Math.max(0, posX) + 'px';
    win.style.top = Math.max(0, posY) + 'px';
    cascadeOffset++;
}

function makeDraggable(win) {
    const header = win.querySelector('.window-header');
    if (!header) return;

    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;

    header.onmousedown = dragMouseDown;

    function dragMouseDown(e) {
        // Não arrasta se clicar em botões no header
        if(e.target.closest('button')) return; 
        
        e.preventDefault();
        pos3 = e.clientX;
        pos4 = e.clientY;
        document.onmouseup = closeDragElement;
        document.onmousemove = elementDrag;
    }

    function elementDrag(e) {
        e.preventDefault();
        pos1 = pos3 - e.clientX;
        pos2 = pos4 - e.clientY;
        pos3 = e.clientX;
        pos4 = e.clientY;
        win.style.top = (win.offsetTop - pos2) + "px";
        win.style.left = (win.offsetLeft - pos1) + "px";
    }

    function closeDragElement() {
        document.onmouseup = null;
        document.onmousemove = null;
    }
}

/* ==========================================================================
   MÓDULO 3: UTILITÁRIOS E FORMATAÇÃO
   ========================================================================== */
function setupFormatters() {
    // Input EAN Generator
    const inputCodigo = document.getElementById('produtoCodigo');
    if (inputCodigo) {
        inputCodigo.addEventListener('click', function() {
            if (this.hasAttribute('readonly')) {
                this.removeAttribute('readonly');
                this.focus();
            } else if (!this.value) {
                this.value = gerarEAN13();
            }
        });
        inputCodigo.addEventListener('blur', function() { this.setAttribute('readonly', 'true'); });
    }

    // Bind Máscaras de Moeda (PDV e Cadastro)
    const inputsMoeda = ['produtoValor', 'inputDesconto', 'inputAcrescimo', 'inputPagDinheiro', 'inputPagPix', 'inputPagCredito', 'inputPagDebito'];
    inputsMoeda.forEach(id => {
        const input = document.getElementById(id);
        if (input) {
            input.addEventListener('input', function() { mascaraMoeda(this); });
            if (id.startsWith('input')) { // Apenas os do PDV calculam totais ao sair
                input.addEventListener('blur', calcularTotaisCaixa);
            }
        }
    });
}

function mascaraMoeda(input) {
    let v = input.value.replace(/\D/g, '');
    if (v === '') { input.value = ''; return; }
    v = (parseFloat(v) / 100).toFixed(2) + '';
    v = v.replace(".", ",");
    v = v.replace(/(\d)(\d{3})(\d{3}),/g, "$1.$2.$3,");
    v = v.replace(/(\d)(\d{3}),/g, "$1.$2,");
    input.value = 'R$ ' + v;
}

function formatarMoeda(valor) {
    if (valor === null || valor === undefined) return "R$ 0,00";
    return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

function converterMoedaParaFloat(valorString) {
    if (!valorString || valorString === '') return 0.0;
    let limpo = valorString.replace(/[^\d,]/g, '').replace(',', '.');
    return parseFloat(limpo) || 0.0;
}

function gerarEAN13() {
    let codigo = "789"; 
    while (codigo.length < 12) codigo += Math.floor(Math.random() * 10);
    let soma = 0;
    for (let i = 0; i < 12; i++) {
        let digito = parseInt(codigo[i]);
        soma += (i % 2 === 0) ? digito * 1 : digito * 3;
    }
    let resto = soma % 10;
    return codigo + (resto === 0 ? 0 : 10 - resto);
}

function getAuthHeader() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('tokenHorus')}`
    };
}

/* ==========================================================================
   MÓDULO 4: GLOBAL MODAL (SPOTLIGHT SEARCH)
   ========================================================================== */
function setupSpotlightSearch() {
    const modal = document.getElementById('modalPesquisa');
    const inputBusca = document.getElementById('inputBuscaModal');
    const closeBtn = document.querySelector('.close-modal');
    let timeoutBusca = null;

    if (!modal || !inputBusca) return;

    // Atalhos Globais
    document.addEventListener('keydown', (e) => {
        // Exemplo: Cmd+K ou Ctrl+K para abrir busca global (Opcional)
        if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
            e.preventDefault();
            abrirSpotlight();
        }
        // ESC para fechar
        if (e.key === 'Escape' && modal.style.display === 'flex') {
            fecharSpotlight();
        }
    });

    closeBtn.addEventListener('click', fecharSpotlight);
    modal.addEventListener('click', (e) => { if (e.target === modal) fecharSpotlight(); });

    // Debounce Search
    inputBusca.addEventListener('input', function() {
        const termo = this.value;
        clearTimeout(timeoutBusca);
        if (termo.length < 2) return;
        
        timeoutBusca = setTimeout(() => buscarProdutosSpotlight(termo), 300);
    });
}

function abrirSpotlight(origem = 'GLOBAL') {
    origemPesquisaSpotlight = origem;
    const modal = document.getElementById('modalPesquisa');
    const input = document.getElementById('inputBuscaModal');
    const lista = document.getElementById('listaResultadosModal');
    
    modal.style.display = 'flex';
    input.value = '';
    lista.innerHTML = '<p style="padding: 24px; text-align:center; color: var(--text-muted);">Digite para buscar no estoque...</p>';
    setTimeout(() => input.focus(), 100);
}

function fecharSpotlight() {
    document.getElementById('modalPesquisa').style.display = 'none';
    window.funcaoSelecaoProduto = null; // Reseta callback customizado
}

async function buscarProdutosSpotlight(termo) {
    const lista = document.getElementById('listaResultadosModal');
    lista.innerHTML = '<p style="padding: 24px; text-align:center;"><i class="ph ph-spinner ph-spin"></i> Buscando...</p>';

    try {
        const res = await fetch(`${API_URL}/api/produtos?nome=${encodeURIComponent(termo)}`, {
            method: 'GET',
            headers: getAuthHeader()
        });
        
        if (res.ok) {
            const produtos = await res.json();
            renderizarResultadosSpotlight(produtos);
        } else {
            throw new Error("Falha na API");
        }
    } catch (e) {
        console.error(e);
        lista.innerHTML = '<p style="padding: 24px; text-align:center; color: var(--danger);">Erro ao buscar produtos.</p>';
    }
}

function renderizarResultadosSpotlight(produtos) {
    const lista = document.getElementById('listaResultadosModal');
    lista.innerHTML = '';

    if (produtos.length === 0) {
        lista.innerHTML = '<p style="padding: 24px; text-align:center; color: var(--text-muted);">Nenhum produto encontrado.</p>';
        return;
    }

    produtos.forEach(prod => {
        const div = document.createElement('div');
        div.className = "item-produto-busca";
        div.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                    <strong style="display:block; color: var(--text-main); font-size: 14px;">${prod.nome}</strong>
                    <span style="color: var(--text-muted); font-size: 12px; font-family: monospace;">SKU: ${prod.codigo || prod.codProduto || 'N/A'}</span>
                </div>
                <div style="font-weight: 600; color: var(--success); font-size: 14px;">
                    ${formatarMoeda(prod.valor)}
                </div>
            </div>
        `;

        div.onclick = () => {
            // Se o PDV (ou Etiquetas) injetou uma função callback, usa ela
            if (typeof window.funcaoSelecaoProduto === 'function') {
                window.funcaoSelecaoProduto(prod);
            } else {
                // Comportamento Global Padrão (Edição rápida)
                abrirJanela('produtosDiv');
                prepararEdicaoProduto(prod.id || prod.codProduto);
            }
            fecharSpotlight();
        };
        lista.appendChild(div);
    });
}

/* ==========================================================================
   MÓDULO 5: GESTÃO DE PRODUTOS (CRUD)
   ========================================================================== */
function setupProdutosModule() {
    // Navegação Interna
    document.getElementById('btnNovoProduto')?.addEventListener('click', () => {
        document.getElementById('formSalvarProduto').reset();
        document.getElementById('produtoId').value = "";
        navegarProdutos('cadastro');
    });
    
    document.querySelector('#conteudoMenuProdutos .menu-card:nth-child(2)')?.addEventListener('click', () => {
        navegarProdutos('consulta');
        buscarProdutosAPI();
    });

    document.getElementById('btnVoltarProdutos')?.addEventListener('click', () => navegarProdutos('menu'));

    // Busca
    document.getElementById('btnPesquisarProduto')?.addEventListener('click', buscarProdutosAPI);
    document.getElementById('filtroNomeProduto')?.addEventListener('keyup', (e) => {
        if (e.key === 'Enter') buscarProdutosAPI();
    });

    // Form Submit (Salvar/Editar)
    document.getElementById('formSalvarProduto')?.addEventListener('submit', handleSalvarProduto);
}

function navegarProdutos(tela) {
    const views = {
        menu: document.getElementById('conteudoMenuProdutos'),
        cadastro: document.getElementById('conteudoFormProdutos'),
        consulta: document.getElementById('conteudoConsultaProdutos')
    };
    const titulo = document.getElementById('tituloJanelaProdutos');
    const btnVoltar = document.getElementById('btnVoltarProdutos');
    const icone = document.getElementById('iconeJanelaPrincipal');

    Object.values(views).forEach(el => { if(el) el.style.display = 'none'; });

    if (tela === 'menu') {
        views.menu.style.display = 'block';
        titulo.innerText = "Cadastro Geral de Produtos";
        btnVoltar.style.display = 'none';
        icone.style.display = 'inline-block';
    } else if (tela === 'cadastro') {
        views.cadastro.style.display = 'block';
        titulo.innerText = "Produtos > Salvar Ficha";
        btnVoltar.style.display = 'flex';
        icone.style.display = 'none';
    } else if (tela === 'consulta') {
        views.consulta.style.display = 'block'; // Usando block ao invés de flex para respeitar o layout da table
        titulo.innerText = "Produtos > Consultar Estoque";
        btnVoltar.style.display = 'flex';
        icone.style.display = 'none';
        setTimeout(() => document.getElementById('filtroNomeProduto').focus(), 100);
    }
}

async function buscarProdutosAPI() {
    const termo = document.getElementById('filtroNomeProduto').value;
    
    // CORREÇÃO: Apontando para o novo container do Grid
    const grid = document.getElementById('gridProdutosCorpo');
    
    // Feedback visual de Loading adaptado para o formato de Grid
    grid.innerHTML = `
        <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--text-muted);">
            <i class="ph ph-spinner ph-spin" style="font-size: 24px; margin-bottom: 8px; display: block; color: var(--primary);"></i>
            Buscando produtos...
        </div>`;

    try {
        const url = termo ? `${API_URL}/api/produtos?nome=${encodeURIComponent(termo)}` : `${API_URL}/api/produtos`;
        const res = await fetch(url, { method: 'GET', headers: getAuthHeader() });
        
        if (res.ok) {
            const lista = await res.json();
            // Chama a função que criamos para desenhar os Cards
            renderizarTabelaProdutos(lista);
        } else {
            grid.innerHTML = '<div style="grid-column: 1 / -1; text-align: center; color: var(--danger); padding: 40px;">Nenhum produto encontrado ou acesso negado.</div>';
        }
    } catch (error) {
        console.error("Erro na busca:", error);
        grid.innerHTML = '<div style="grid-column: 1 / -1; text-align: center; color: var(--danger); padding: 40px;">Erro de conexão com o servidor.</div>';
    }
}

/* ==========================================================================
   RENDERIZAÇÃO DOS CARDS (GRID)
   ========================================================================== */
function renderizarTabelaProdutos(lista) { // Mantive o nome original que a busca chama
    const grid = document.getElementById('gridProdutosCorpo');
    grid.innerHTML = ''; 

    if (lista.length === 0) {
        grid.innerHTML = '<div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--text-muted);">Nenhum produto encontrado.</div>';
        return;
    }

    lista.forEach(prod => {
        const idReal = prod.id || prod.codProduto;
        const estoqueAtual = prod.quantidadeEstoque || 0;
        const estoqueClass = estoqueAtual <= 5 ? 'low-stock' : ''; // Destaque visual se estiver acabando

        const card = document.createElement('div');
        card.className = 'product-card';
        card.innerHTML = `
            <div class="product-card-header">
                <div>
                    <h3 class="product-title">${prod.nome}</h3>
                    <span class="product-sku">SKU: ${prod.codigo || 'N/A'}</span>
                </div>
                <div class="product-actions-top">
                    <button class="btn-icon" onclick="prepararEdicaoProduto(${idReal})" title="Editar"><i class="ph ph-pencil-simple"></i></button>
                    <button class="btn-icon" onclick="deletarProduto(${idReal})" title="Excluir" style="color: var(--danger);"><i class="ph ph-trash"></i></button>
                </div>
            </div>
            
            <div class="product-price">${formatarMoeda(prod.valor)}</div>
            
            <div class="product-card-footer">
                <div class="stock-status">
                    <span class="stock-label">Estoque atual:</span>
                    <span class="stock-amount ${estoqueClass}" id="display_estoque_${idReal}">${estoqueAtual}</span>
                </div>
                
                <div class="stock-control-pill">
                    <button onclick="ajustarEstoqueRapido(${idReal}, 'sub')" title="Subtrair do estoque">
                        <i class="ph ph-minus"></i>
                    </button>
                    
                    <input type="number" id="input_ajuste_${idReal}" placeholder="Qtd" min="1">
                    
                    <button onclick="ajustarEstoqueRapido(${idReal}, 'add')" title="Adicionar ao estoque">
                        <i class="ph ph-plus"></i>
                    </button>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
}

/* ==========================================================================
   LÓGICA DE NEGÓCIO: AJUSTE RÁPIDO DE ESTOQUE
   ========================================================================== */
async function ajustarEstoqueRapido(idProduto, operacao) {
    const displayEstoque = document.getElementById(`display_estoque_${idProduto}`);
    const inputAjuste = document.getElementById(`input_ajuste_${idProduto}`);
    
    const estoqueAtual = parseInt(displayEstoque.innerText, 10);
    const qtdMovimentar = parseInt(inputAjuste.value, 10) || 1; 

    if (qtdMovimentar <= 0) {
        alert("Digite uma quantidade válida.");
        inputAjuste.value = '';
        return;
    }

    let novoEstoque = (operacao === 'add') ? estoqueAtual + qtdMovimentar : estoqueAtual - qtdMovimentar;

    if (novoEstoque < 0) {
        alert(`Saldo insuficiente! Estoque atual: ${estoqueAtual}`);
        inputAjuste.value = '';
        return;
    }

    // UI Feedback
    inputAjuste.disabled = true;
    inputAjuste.placeholder = "...";

    try {
        // Recupera o token para a autenticação
        const token = localStorage.getItem('tokenHorus');

        const res = await fetch(`${API_URL}/api/produtos/${idProduto}`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}` // Inclusão do Token
            },
            body: JSON.stringify({
                quantidadeEstoque: novoEstoque
            })
        });

        if (res.ok) {
            displayEstoque.innerText = novoEstoque;
            displayEstoque.classList.toggle('low-stock', novoEstoque <= 5);
            
            // Feedback visual de sucesso
            displayEstoque.style.color = 'var(--success)';
            setTimeout(() => displayEstoque.style.color = '', 800);
            inputAjuste.value = '';
        } else {
            const erro = await res.text();
            alert("Erro de autorização ou permissão: " + erro);
        }
    } catch (e) {
        console.error(e);
        alert("Erro de conexão ao tentar atualizar o estoque.");
    } finally {
        inputAjuste.disabled = false;
        inputAjuste.placeholder = "Qtd";
    }
}

async function handleSalvarProduto(e) {
    e.preventDefault();
    const id = document.getElementById('produtoId').value;
    const btn = e.target.querySelector('button[type="submit"]');
    const txtOriginal = btn.innerHTML;
    
    // CAPTURA DO NOVO CAMPO: ParseInt com fallback para 0
    const qtdEstoque = parseInt(document.getElementById('produtoQuantidade').value, 10) || 0;

    const dto = {
        codigo: document.getElementById('produtoCodigo').value,
        nome: document.getElementById('produtoNome').value,
        valor: converterMoedaParaFloat(document.getElementById('produtoValor').value),
        quantidadeEstoque: qtdEstoque // <- Enviando para a API
    };

    btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Salvando...';
    btn.disabled = true;

    try {
        const method = id ? 'PUT' : 'POST';
        const url = id ? `${API_URL}/api/produtos/${id}` : `${API_URL}/api/produtos`;

        const res = await fetch(url, {
            method: method,
            headers: getAuthHeader(),
            body: JSON.stringify(dto)
        });

        if (res.ok) {
            navegarProdutos('consulta');
            buscarProdutosAPI();
        } else {
            alert("Erro ao salvar: Verifique os dados.");
        }
    } catch (e) {
        alert("Erro de conexão ao salvar.");
    } finally {
        btn.innerHTML = txtOriginal;
        btn.disabled = false;
    }
}

async function prepararEdicaoProduto(id) {
    try {
        const res = await fetch(`${API_URL}/api/produtos/${id}`, { method: 'GET', headers: getAuthHeader() });
        if (!res.ok) throw new Error('Erro na API');
        
        const produto = await res.json();
        document.getElementById('produtoId').value = produto.id || produto.codProduto;
        document.getElementById('produtoCodigo').value = produto.codigo || '';
        document.getElementById('produtoNome').value = produto.nome || '';
        
        // PREENCHIMENTO DO NOVO CAMPO NA EDIÇÃO
        document.getElementById('produtoQuantidade').value = produto.quantidadeEstoque || 0;
        
        // Pega o input, insere o valor bruto e simula o evento de máscara
        const inputValor = document.getElementById('produtoValor');
        inputValor.value = produto.valor ? (produto.valor * 100).toFixed(0) : ''; 
        mascaraMoeda(inputValor); 

        navegarProdutos('cadastro');
    } catch (e) {
        alert("Erro ao carregar dados do produto.");
    }
}

async function prepararEdicaoProduto(id) {
    try {
        const res = await fetch(`${API_URL}/api/produtos/${id}`, { method: 'GET', headers: getAuthHeader() });
        if (!res.ok) throw new Error('Erro na API');
        
        const produto = await res.json();
        document.getElementById('produtoId').value = produto.id || produto.codProduto;
        document.getElementById('produtoCodigo').value = produto.codigo || '';
        document.getElementById('produtoNome').value = produto.nome || '';
        
        // Pega o input, insere o valor bruto e simula o evento de máscara
        const inputValor = document.getElementById('produtoValor');
        inputValor.value = produto.valor ? (produto.valor * 100).toFixed(0) : ''; 
        mascaraMoeda(inputValor); 

        navegarProdutos('cadastro');
    } catch (e) {
        alert("Erro ao carregar dados do produto.");
    }
}

async function deletarProduto(id) {
    if (!confirm("Excluir este produto definitivamente?")) return;
    try {
        const res = await fetch(`${API_URL}/api/produtos/${id}`, { method: 'DELETE', headers: getAuthHeader() });
        if (res.ok || res.status === 204) buscarProdutosAPI();
        else alert("Erro: O produto pode estar vinculado a vendas antigas.");
    } catch (e) {
        alert("Erro de conexão.");
    }
}

/* ==========================================================================
   MÓDULO 6: FRENTE DE CAIXA (PDV)
   ========================================================================== */
function setupPDVModule() {
    // Leitor de Barras
    const inputLeitor = document.getElementById('inputCodigoBarras');
    if (inputLeitor) {
        inputLeitor.addEventListener('keydown', async (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                await processarCodigoBarras(inputLeitor.value.trim());
            }
        });
    }

    // Atalhos PDV
    document.addEventListener('keydown', (e) => {
        const caixa = document.getElementById('caixaDiv');
        if (caixa && caixa.style.display !== 'none' && e.key === 'F2') {
            e.preventDefault();
            toggleLeitorBarras();
        }
    });
}

function toggleLeitorBarras() {
    const painel = document.getElementById('painelLeitorBarras');
    const input = document.getElementById('inputCodigoBarras');
    if (painel.style.display === 'none') {
        painel.style.display = 'block';
        input.value = '';
        input.focus();
    } else {
        painel.style.display = 'none';
    }
}

// Interceptador para busca manual no PDV via Spotlight
window.abrirModalPesquisaParaCaixa = function() {
    abrirSpotlight('CAIXA');
    window.funcaoSelecaoProduto = function(produto) {
        adicionarAoCarrinhoPDV(produto);
    };
};

async function processarCodigoBarras(codigo) {
    if (!codigo) return;
    const input = document.getElementById('inputCodigoBarras');
    input.disabled = true;

    try {
        const res = await fetch(`${API_URL}/api/produtos/codigo/${encodeURIComponent(codigo)}`, {
            method: 'GET', headers: getAuthHeader()
        });
        
        if (res.ok) {
            const produto = await res.json();
            adicionarAoCarrinhoPDV(produto, 1); // Modo Auto (1 unid)
            input.value = '';
        } else if (res.status === 404) {
            alert(`Produto não encontrado: ${codigo}`);
            input.value = '';
        }
    } catch (error) {
        alert("Erro ao buscar código de barras.");
    } finally {
        input.disabled = false;
        input.focus();
    }
}

function adicionarAoCarrinhoPDV(produto, qtdAutomatica = null) {
    let qtd = qtdAutomatica;
    if (qtd === null) {
        let input = prompt(`Adicionar "${produto.nome}"\nQuantidade:`, "1");
        if (input === null) return;
        qtd = parseInt(input);
    }
    
    if (isNaN(qtd) || qtd <= 0) { alert("Quantidade inválida."); return; }

    const id = produto.id || produto.codProduto;
    const index = carrinhoPDV.findIndex(item => item.codProduto == id);

    if (index >= 0) {
        carrinhoPDV[index].quantidade += qtd;
    } else {
        carrinhoPDV.push({
            codProduto: id,
            nome: produto.nome,
            valorUnitario: produto.valor,
            quantidade: qtd
        });
    }
    renderizarCarrinhoPDV();
}

function removerDoCarrinhoPDV(index) {
    carrinhoPDV.splice(index, 1);
    renderizarCarrinhoPDV();
}

function renderizarCarrinhoPDV() {
    const tbody = document.getElementById('tabelaCarrinho');
    tbody.innerHTML = '';
    let subtotal = 0;

    carrinhoPDV.forEach((item, idx) => {
        const totalItem = item.quantidade * item.valorUnitario;
        subtotal += totalItem;

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>
                <span style="font-weight: 500;">${item.nome}</span>
                ${item.quantidade > 1 ? '<br><small style="color:var(--success); font-size:10px;">Item agrupado</small>' : ''}
            </td>
            <td class="align-center">${item.quantidade}</td>
            <td class="align-right">${formatarMoeda(item.valorUnitario)}</td>
            <td class="align-right" style="font-weight: 600;">${formatarMoeda(totalItem)}</td>
            <td class="align-center">
                <button class="btn-icon" onclick="removerDoCarrinhoPDV(${idx})" style="color: var(--danger);"><i class="ph ph-trash"></i></button>
            </td>
        `;
        tbody.appendChild(tr);
    });

    document.getElementById('lblSubtotal').innerText = formatarMoeda(subtotal);
    calcularTotaisCaixa();
}

function calcularTotaisCaixa() {
    const subtotal = carrinhoPDV.reduce((acc, item) => acc + (item.quantidade * item.valorUnitario), 0);
    const desc = converterMoedaParaFloat(document.getElementById('inputDesconto').value);
    const acres = converterMoedaParaFloat(document.getElementById('inputAcrescimo').value);
    
    const totalVenda = subtotal + acres - desc;
    
    document.getElementById('lblTotalFinal').innerText = formatarMoeda(totalVenda);
    document.getElementById('lblTotalFinal').style.color = totalVenda < 0 ? 'var(--danger)' : 'var(--success)';

    const pgDin = converterMoedaParaFloat(document.getElementById('inputPagDinheiro').value);
    const pgPix = converterMoedaParaFloat(document.getElementById('inputPagPix').value);
    const pgCred = converterMoedaParaFloat(document.getElementById('inputPagCredito').value);
    const pgDeb = converterMoedaParaFloat(document.getElementById('inputPagDebito').value);

    const totalPago = pgDin + pgPix + pgCred + pgDeb;
    document.getElementById('inputValorPago').value = formatarMoeda(totalPago);

    let diff = totalPago - totalVenda;
    document.getElementById('lblAreceber').innerText = formatarMoeda(diff < 0 ? Math.abs(diff) : 0);
    document.getElementById('lblTroco').innerText = formatarMoeda(diff >= 0 ? diff : 0);
}

async function finalizarVenda() {
    if (carrinhoPDV.length === 0) { alert("O carrinho está vazio."); return; }

    const subtotal = carrinhoPDV.reduce((acc, item) => acc + (item.quantidade * item.valorUnitario), 0);
    const desc = converterMoedaParaFloat(document.getElementById('inputDesconto').value);
    const acres = converterMoedaParaFloat(document.getElementById('inputAcrescimo').value);
    const totalFinal = Number((subtotal + acres - desc).toFixed(2));

    const vDin = converterMoedaParaFloat(document.getElementById('inputPagDinheiro').value);
    const vPix = converterMoedaParaFloat(document.getElementById('inputPagPix').value);
    const vCred = converterMoedaParaFloat(document.getElementById('inputPagCredito').value);
    const vDeb = converterMoedaParaFloat(document.getElementById('inputPagDebito').value);
    const totalPago = Number((vDin + vPix + vCred + vDeb).toFixed(2));

    if (totalPago < totalFinal) {
        alert(`Pagamento insuficiente.\nFalta: ${formatarMoeda(totalFinal - totalPago)}`);
        return;
    }

    const btn = document.querySelector('.btn-checkout');
    const txtOriginal = btn.innerHTML;
    btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Processando...';
    btn.disabled = true;

    const payload = {
        qtdParcelas: parseInt(document.getElementById('inputParcelas').value) || 1,
        desconto: desc,
        acrescimo: acres,
        valorDinheiro: vDin, valorPix: vPix, valorCredito: vCred, valorDebito: vDeb,
        itens: carrinhoPDV.map(i => ({ codProduto: i.codProduto, quantidade: i.quantidade, valorUnitario: i.valorUnitario }))
    };

    try {
        const res = await fetch(`${API_URL}/api/vendas`, {
            method: 'POST', headers: getAuthHeader(), body: JSON.stringify(payload)
        });

        if (res.ok) {
            alert("✅ Venda registrada com sucesso!");
            
            // Limpeza Geral do PDV
            carrinhoPDV = [];
            renderizarCarrinhoPDV();
            ['inputDesconto', 'inputAcrescimo', 'inputPagDinheiro', 'inputPagPix', 'inputPagCredito', 'inputPagDebito'].forEach(id => {
                document.getElementById(id).value = '';
            });
            document.getElementById('inputParcelas').value = '1';
            calcularTotaisCaixa();
        } else {
            const erro = await res.text();
            alert("Erro na API: " + erro);
        }
    } catch (e) {
        alert("Erro de conexão ao finalizar venda.");
    } finally {
        btn.innerHTML = txtOriginal;
        btn.disabled = false;
    }
}

/* ==========================================================================
   MÓDULO 7: RELATÓRIOS E ETIQUETAS
   ========================================================================== */
function setupRelatoriosModule() {
    // Navegação
    document.getElementById('btnGerarBarcode')?.addEventListener('click', () => navegarRelatorios('barcode'));
    document.getElementById('btnAbrirEtiqueta')?.addEventListener('click', () => navegarRelatorios('etiqueta'));
    document.getElementById('btnAbrirRelVendas')?.addEventListener('click', () => {
        navegarRelatorios('vendas');
        carregarProdutosParaFiltroVendas();
        const hoje = new Date();
        document.getElementById('relDataInicio').valueAsDate = hoje;
        document.getElementById('relDataFim').valueAsDate = hoje;
    });
    document.getElementById('btnVoltarRelatorios')?.addEventListener('click', () => navegarRelatorios('menu'));

    // Integração Spotlight -> Form de Etiquetas
    document.getElementById('btnAbrirModal')?.addEventListener('click', () => {
        abrirSpotlight('RELATORIO');
        window.funcaoSelecaoProduto = function(produto) {
            document.getElementById('displayNomeProduto').value = produto.nome;
            document.getElementById('etiquetaIdProduto').value = produto.id || produto.codProduto;
        };
    });

    // Submits
    document.getElementById('formEtiqueta')?.addEventListener('submit', handleGerarEtiquetas);
}

function navegarRelatorios(tela) {
    const views = {
        menu: document.getElementById('conteudoMenuRelatorios'),
        barcode: document.getElementById('conteudoFormBarcode'),
        etiqueta: document.getElementById('conteudoFormEtiqueta'),
        vendas: document.getElementById('conteudoFormVendas')
    };

    Object.values(views).forEach(el => { if(el) el.style.display = 'none'; });
    
    const titulo = document.getElementById('tituloJanelaRelatorios');
    const btnVoltar = document.getElementById('btnVoltarRelatorios');
    const icone = document.getElementById('iconeJanelaRelatorios');

    if (tela === 'menu') {
        views.menu.style.display = 'block';
        titulo.innerText = "Central de Relatórios";
        btnVoltar.style.display = 'none';
        icone.style.display = 'inline-block';
    } else {
        views[tela].style.display = 'block';
        titulo.innerText = `Relatórios > ${tela.charAt(0).toUpperCase() + tela.slice(1)}`;
        btnVoltar.style.display = 'flex';
        icone.style.display = 'none';
    }
}

async function handleGerarEtiquetas(e) {
    e.preventDefault();
    const id = document.getElementById('etiquetaIdProduto').value;
    const qtd = document.getElementById('etiquetaQtdCopias').value;

    if (!id) { alert("Selecione um produto via lupa primeiro."); return; }
    if (qtd > 320) { alert("Limite máximo por requisição é 320 (20 páginas)."); return; }

    const btn = e.target.querySelector('button');
    const txt = btn.innerHTML;
    btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Gerando...';
    btn.disabled = true;

    try {
        const res = await fetch(`${API_URL}/api/etiquetas/gerar/${id}?qtd=${qtd}`, {
            method: 'GET', headers: getAuthHeader()
        });

        if (res.ok) {
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            window.open(url, '_blank');
            setTimeout(() => window.URL.revokeObjectURL(url), 5000);
        } else {
            alert("Falha no servidor ao processar PDF.");
        }
    } catch (err) {
        alert("Erro de conexão ao gerar etiqueta.");
    } finally {
        btn.innerHTML = txt;
        btn.disabled = false;
    }
}

// Lógicas de Vendas (Checkboxes) e PDF
async function carregarProdutosParaFiltroVendas() {
    const divLista = document.getElementById('listaProdutosCheck');
    divLista.innerHTML = '<span style="font-size:12px; color:var(--text-muted);">Carregando produtos...</span>';
    try {
        const res = await fetch(`${API_URL}/api/produtos`, { method: 'GET', headers: getAuthHeader() });
        if(!res.ok) throw new Error();
        const produtos = await res.json();
        
        divLista.innerHTML = produtos.length === 0 ? '<span style="font-size:12px">Estoque vazio.</span>' : '';
        
        produtos.forEach(prod => {
            const id = prod.id || prod.codProduto;
            divLista.innerHTML += `
                <div style="display:flex; align-items:center; margin-bottom:8px; border-bottom:1px solid var(--border-subtle); padding-bottom:4px;">
                    <input type="checkbox" name="prodFiltro" value="${id}" id="chk_${id}" checked style="margin-right: 8px;">
                    <label for="chk_${id}" style="font-size: 13px; cursor: pointer;">${prod.nome}</label>
                </div>
            `;
        });
    } catch (e) {
        divLista.innerHTML = '<span style="color:var(--danger); font-size:12px">Erro ao buscar dados.</span>';
    }
}

function marcarTodosProdutos(marcar) {
    document.querySelectorAll('input[name="prodFiltro"]').forEach(chk => chk.checked = marcar);
}

/**
 * GERA O PDF DO RELATÓRIO DE VENDAS
 * - Busca dados, Filtra no Front, Monta o HTML e Abre em Nova Guia (Blob)
 * - Exibe Desconto e Acréscimo lado a lado.
 */
async function gerarRelatorioVendasPDF() {
    const dataInicio = document.getElementById('relDataInicio').value;
    const dataFim = document.getElementById('relDataFim').value;
    const formaPagamento = document.getElementById('relFormaPagamento').value;
    
    const checkboxes = document.querySelectorAll('input[name="prodFiltro"]:checked');
    const produtosSelecionados = Array.from(checkboxes).map(c => String(c.value)); 

    if (!dataInicio || !dataFim) { alert("Selecione o período."); return; }
    if (produtosSelecionados.length === 0) { alert("Selecione pelo menos um produto."); return; }

    const btn = document.querySelector('#formRelatorioVendas button'); 
    const txtOriginal = btn ? btn.innerHTML : 'Gerar';
    if(btn) btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Processando...';

    const token = localStorage.getItem('tokenHorus');

    try {
        const res = await fetch(`${API_URL}/api/vendas`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        }); 
        
        if(!res.ok) throw new Error("Erro ao buscar vendas na API");
        
        const todasVendas = await res.json();

        const vendasFiltradas = todasVendas.filter(venda => {
            const dataVenda = new Date(venda.dataVenda).toISOString().split('T')[0];
            if (dataVenda < dataInicio || dataVenda > dataFim) return false;

            if (formaPagamento !== "TODAS") {
                if (formaPagamento === "DINHEIRO" && (!venda.valorDinheiro || venda.valorDinheiro <= 0)) return false;
                if (formaPagamento === "PIX" && (!venda.valorPix || venda.valorPix <= 0)) return false;
                if (formaPagamento === "CREDITO" && (!venda.valorCredito || venda.valorCredito <= 0)) return false;
                if (formaPagamento === "DEBITO" && (!venda.valorDebito || venda.valorDebito <= 0)) return false;
            }

            const temProdutoSelecionado = venda.itens.some(item => {
                const idProdItem = String(item.codProduto || item.produto?.codProduto || item.produto?.id);
                return produtosSelecionados.includes(idProdItem);
            });
            
            return temProdutoSelecionado;
        });

        if (vendasFiltradas.length === 0) {
            alert("Nenhuma venda encontrada com esses filtros.");
            return;
        }

        let htmlConteudo = '';
        let totalGeralPeriodo = 0;

        vendasFiltradas.forEach(venda => {
            totalGeralPeriodo += venda.valorTotal;
            const dataF = new Date(venda.dataVenda).toLocaleString('pt-BR');

            let pagtos = [];
            if(venda.valorDinheiro > 0) pagtos.push(`Dinheiro`);
            if(venda.valorPix > 0) pagtos.push(`PIX`);
            if(venda.valorCredito > 0) pagtos.push(`Crédito`);
            if(venda.valorDebito > 0) pagtos.push(`Débito`);
            
            const textoPagto = pagtos.length > 0 ? pagtos.join(', ') : 'Não informado';

            htmlConteudo += `
                <div class="venda-box">
                    <div class="venda-header">
                        <span><strong>Venda #${venda.codVenda || venda.id}</strong> <small>(${dataF})</small></span>
                        <span>Total: <strong>${formatarMoeda(venda.valorTotal)}</strong></span>
                    </div>
                    <div class="venda-info">
                        Forma(s): ${textoPagto} | Itens: ${venda.itens.length} | Desconto: ${formatarMoeda(venda.desconto)} | Acréscimo: ${formatarMoeda(venda.acrescimo)}
                    </div>
                    <table class="tabela-itens">
                        <thead>
                            <tr>
                                <th>Produto</th>
                                <th width="50" style="text-align:center">Qtd</th>
                                <th width="80" style="text-align:right">Unit.</th>
                                <th width="80" style="text-align:right">Subtotal</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${venda.itens.map(item => `
                                <tr>
                                    <td>${item.nome || item.produto?.nome || 'Item Cód ' + item.codProduto}</td>
                                    <td style="text-align:center">${item.quantidade}</td>
                                    <td style="text-align:right">${formatarMoeda(item.valorUnitario)}</td>
                                    <td style="text-align:right">${formatarMoeda((item.valorTotalItem || item.quantidade * item.valorUnitario))}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            `;
        });

        const htmlFinal = `
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <title>Relatório de Vendas - Horus</title>
                <style>
                    body { font-family: 'Inter', -apple-system, sans-serif; padding: 40px; font-size: 12px; color: #111827; line-height: 1.4; background: #fff; }
                    .header { text-align: center; margin-bottom: 24px; border-bottom: 2px solid #111827; padding-bottom: 16px; }
                    .header h1 { margin: 0; font-size: 20px; text-transform: uppercase; letter-spacing: 0.5px; }
                    .filtros { margin-bottom: 24px; background: #f3f4f6; padding: 16px; border-radius: 8px; font-size: 11px; color: #4b5563; }
                    .venda-box { border: 1px solid #e5e7eb; margin-bottom: 20px; page-break-inside: avoid; border-radius: 8px; overflow: hidden; }
                    .venda-header { background: #f9fafb; padding: 12px 16px; display: flex; justify-content: space-between; border-bottom: 1px solid #e5e7eb; font-size: 13px; }
                    .venda-info { padding: 8px 16px; font-size: 11px; color: #6b7280; border-bottom: 1px solid #f3f4f6; }
                    .tabela-itens { width: 100%; border-collapse: collapse; }
                    .tabela-itens th { background: #fff; text-align: left; padding: 8px 16px; font-size: 10px; color: #9ca3af; text-transform: uppercase; border-bottom: 1px solid #f3f4f6; }
                    .tabela-itens td { padding: 10px 16px; border-bottom: 1px solid #f9fafb; font-size: 12px; }
                    .tabela-itens tr:last-child td { border-bottom: none; }
                    .total-geral { text-align: right; font-size: 18px; margin-top: 32px; font-weight: 700; padding: 16px; background: #111827; color: #fff; border-radius: 8px; }
                    @media print { body { padding: 0; } .no-print { display: none; } }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Relatório Analítico de Vendas</h1>
                    <div style="margin-top: 4px; font-size: 12px; color: #6b7280;">Sistema de Gestão Horus</div>
                </div>
                <div class="filtros">
                    <strong>Período:</strong> ${dataInicio.split('-').reverse().join('/')} até ${dataFim.split('-').reverse().join('/')} <br>
                    <strong>Filtro Pagamento:</strong> ${formaPagamento} <br>
                    <strong>Data de Emissão:</strong> ${new Date().toLocaleString('pt-BR')}
                </div>
                ${htmlConteudo}
                <div class="total-geral">
                    TOTAL VENDIDO NO PERÍODO: ${formatarMoeda(totalGeralPeriodo)}
                </div>
                <script>
                    window.onload = function() { window.print(); }
                </script>
            </body>
            </html>
        `;

        const blob = new Blob([htmlFinal], { type: 'text/html;charset=utf-8' });
        const url = window.URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => window.URL.revokeObjectURL(url), 5000);

    } catch (e) {
        console.error(e);
        alert("Erro ao gerar relatório: " + e.message);
    } finally {
        if(btn) {
            btn.innerHTML = txtOriginal;
            btn.disabled = false;
        }
    }
}       

/* =================================================================================
   7. IDENTIFICAÇÃO DO UTILIZADOR (BADGE SUPERIOR DIREITO)
   ================================================================================= */
function atualizarBadgeIdentificacao(nomeEmpresa, nomeUsuario) {
    const elEmpresa = document.getElementById('nomeEmpresaBadge');
    const elUsuario = document.getElementById('nomeUsuarioBadge');
    
    if (elEmpresa) elEmpresa.innerText = nomeEmpresa || 'Empresa';
    if (elUsuario) elUsuario.innerText = nomeUsuario || 'Operador';
}

function carregarIdentificacaoSessao() {
    const empresaSalva = localStorage.getItem('horus_empresa_nome') || 'SaaS Workspace';
    const usuarioSalvo = localStorage.getItem('horus_usuario_nome') || 'Utilizador Ativo';
    atualizarBadgeIdentificacao(empresaSalva, usuarioSalvo);
}

/* ==========================================================================
   MÓDULO 8: DASHBOARD DE VENDAS (DATA VIZ)
   ========================================================================== */
let chartReceitaInstance = null;
let chartProdutosInstance = null;
let dashboardDataCache = null; // Cache para evitar requisições repetidas ao trocar o filtro rapidamente

function setupDashboardModule() {
    document.getElementById('dashboardBtn')?.addEventListener('click', () => {
        carregarDashboardDados(true); // Força busca fresca na API ao clicar no menu
    });
}

// Controla a exibição dos campos personalizados e dispara a atualização
function toggleFiltroPersonalizado() {
    const select = document.getElementById('dashFiltroTempo');
    const container = document.getElementById('dashFiltroPersonalizadoContainer');
    
    if (select.value === 'CUSTOM') {
        container.style.display = 'flex';
        // Inicia com as datas de hoje preenchidas se estiverem vazias
        if (!document.getElementById('dashDataInicio').value) {
            const hojeStr = new Date().toISOString().split('T')[0];
            document.getElementById('dashDataInicio').value = hojeStr;
            document.getElementById('dashDataFim').value = hojeStr;
        }
    } else {
        container.style.display = 'none';
    }
    carregarDashboardDados(false); // Usa os dados do cache local ao mudar o filtro básico
}

async function carregarDashboardDados(forceFetch = false) {
    const btnIcon = document.querySelector('#dashboardBtn i');
    if(btnIcon) btnIcon.className = "ph ph-spinner ph-spin";

    try {
        if (forceFetch || !dashboardDataCache) {
            const token = localStorage.getItem('tokenHorus');
            const res = await fetch(`${API_URL}/api/vendas`, {
                method: 'GET',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (!res.ok) throw new Error("Erro ao buscar dados do dashboard.");
            dashboardDataCache = await res.json();
        }

        const filtroVal = document.getElementById('dashFiltroTempo').value;
        processarERenderizarDashboard(dashboardDataCache, filtroVal);

    } catch (e) {
        console.error(e);
        alert("Erro ao carregar Dashboard: " + e.message);
    } finally {
        if(btnIcon) btnIcon.className = "ph ph-squares-four";
    }
}

function processarERenderizarDashboard(todasVendas, filtroVal) {
    let hoje = new Date();
    hoje.setHours(23, 59, 59, 999);
    
    let dataCorte = new Date();
    dataCorte.setHours(0, 0, 0, 0);

    // Lógica Avançada de Tratamento do Período
    if (filtroVal === 'CUSTOM') {
        const valInicio = document.getElementById('dashDataInicio').value;
        const valFim = document.getElementById('dashDataFim').value;
        
        // Uso de literal 'T00:00:00' para travar o fuso horário no timezone local do navegador
        if (valInicio) dataCorte = new Date(valInicio + 'T00:00:00');
        if (valFim) hoje = new Date(valFim + 'T23:59:59');
    } else {
        const dias = parseInt(filtroVal, 10);
        if (dias > 0) {
            dataCorte.setDate(dataCorte.getDate() - dias);
        }
    }

    // Filtra as vendas com base nas datas calculadas
    const vendasPeriodo = todasVendas.filter(venda => {
        const d = new Date(venda.dataVenda);
        return d >= dataCorte && d <= hoje;
    });

    let receitaTotal = 0;
    let volumeVendas = vendasPeriodo.length;
    let vendasPorDia = {};
    let produtosVendidos = {};

    vendasPeriodo.forEach(venda => {
        receitaTotal += venda.valorTotal;

        const dataF = new Date(venda.dataVenda).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
        vendasPorDia[dataF] = (vendasPorDia[dataF] || 0) + venda.valorTotal;

        if (venda.itens) {
            venda.itens.forEach(item => {
                const nomeProd = item.nome || item.produto?.nome || 'Desconhecido';
                produtosVendidos[nomeProd] = (produtosVendidos[nomeProd] || 0) + item.quantidade;
            });
        }
    });

    const ticketMedio = volumeVendas > 0 ? (receitaTotal / volumeVendas) : 0;
    
    document.getElementById('kpiReceita').innerText = formatarMoeda(receitaTotal);
    document.getElementById('kpiTicket').innerText = formatarMoeda(ticketMedio);
    document.getElementById('kpiVolume').innerText = volumeVendas;

    // Inverte a amostragem cronológica das barras para refletir da esquerda para a direita corretamente
    const labelsReceita = Object.keys(vendasPorDia).reverse(); 
    const dadosReceita = Object.values(vendasPorDia).reverse();

    const arrayProdutos = Object.entries(produtosVendidos).sort((a, b) => b[1] - a[1]).slice(0, 5);
    const labelsProdutos = arrayProdutos.map(p => p[0]);
    const dadosProdutos = arrayProdutos.map(p => p[1]);

    renderizarChartReceita(labelsReceita, dadosReceita);
    renderizarChartProdutos(labelsProdutos, dadosProdutos);
}

function renderizarChartReceita(labels, dados) {
    const ctx = document.getElementById('chartReceita').getContext('2d');
    if (chartReceitaInstance) chartReceitaInstance.destroy();

    // Degradê Premium para a Barra
    const gradient = ctx.createLinearGradient(0, 0, 0, 400);
    gradient.addColorStop(0, 'rgba(17, 24, 39, 0.8)'); // var(--text-main) Escuro
    gradient.addColorStop(1, 'rgba(17, 24, 39, 0.2)');

    chartReceitaInstance = new Chart(ctx, {
        type: 'line', // Usando linha com preenchimento (estilo SaaS Premium)
        data: {
            labels: labels,
            datasets: [{
                label: 'Receita Diária (R$)',
                data: dados,
                backgroundColor: gradient,
                borderColor: '#111827',
                borderWidth: 2,
                pointBackgroundColor: '#fff',
                pointBorderColor: '#111827',
                pointRadius: 4,
                fill: true,
                tension: 0.3 // Deixa a linha suave
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: { callback: function(value) { return 'R$ ' + value; } }
                }
            }
        }
    });
}

function renderizarChartProdutos(labels, dados) {
    const ctx = document.getElementById('chartProdutos').getContext('2d');
    if (chartProdutosInstance) chartProdutosInstance.destroy();

    chartProdutosInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: dados,
                backgroundColor: [
                    '#111827', // Preto/Cinza Escuro
                    '#374151', // Cinza Médio
                    '#6B7280', // Cinza Base
                    '#9CA3AF', // Cinza Claro
                    '#E5E7EB'  // Muito claro
                ],
                borderWidth: 0,
                hoverOffset: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '65%', // Deixa o furo da pizza maior e mais elegante
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { usePointStyle: true, boxWidth: 8, font: { size: 11 } }
                }
            }
        }
    });
}