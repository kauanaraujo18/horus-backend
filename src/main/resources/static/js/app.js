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
    setupContasPagarModule();
    setupProducaoModule();
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
        'contasPagarBtn': 'contasPagarDiv'
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
    if (winId === 'contasPagarDiv') cpNavegar('menu');

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
    const inputsMoeda = ['produtoValor', 'produtoValorCusto', 'inputDesconto', 'inputAcrescimo', 'inputPagDinheiro', 'inputPagPix', 'inputPagCredito', 'inputPagDebito'];
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

    // No contexto do caixa, filtra matérias-primas (não podem ser vendidas diretamente)
    const listagem = (origemPesquisaSpotlight === 'CAIXA')
        ? produtos.filter(p => p.tipo !== 'MP')
        : produtos;

    if (listagem.length === 0) {
        lista.innerHTML = '<p style="padding: 24px; text-align:center; color: var(--text-muted);">Nenhum produto encontrado.</p>';
        return;
    }

    listagem.forEach(prod => {
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
/* ==========================================================================
   MÓDULO 5: GESTÃO DE PRODUTOS (CRUD)
   ========================================================================== */
function setupProdutosModule() {
    // Navegação Interna
    document.getElementById('btnNovoProduto')?.addEventListener('click', () => {
        document.getElementById('formSalvarProduto').reset();
        document.getElementById('produtoId').value = "";
        document.getElementById('produtoTipo').value = 'R';
        document.getElementById('produtoUnidadeMedida').value = '';
        document.getElementById('produtoReferencia').innerHTML = '<option value="">-- Selecione a unidade primeiro --</option>';
        document.getElementById('produtoReferencia').disabled = true;
        document.getElementById('secaoMateriasPrimas').style.display = 'none';
        navegarProdutos('cadastro');
    });
    
    document.querySelector('#conteudoMenuProdutos .menu-card:nth-child(2)')?.addEventListener('click', () => {
        navegarProdutos('consulta');
        buscarProdutosAPI();
    });

    document.getElementById('btnEsquemaProducoes')?.addEventListener('click', abrirEsquemaProducoes);

    document.getElementById('btnVoltarProdutos')?.addEventListener('click', () => navegarProdutos('menu'));

    // ---> NOVA LÓGICA DE BUSCA EM TEMPO REAL (DEBOUNCE) <---
    let timeoutBuscaGrid = null;
    const inputFiltroGrid = document.getElementById('filtroNomeProduto');
    const btnPesquisarGrid = document.getElementById('btnPesquisarProduto');

    // Ao clicar no botão (Busca imediata)
    btnPesquisarGrid?.addEventListener('click', buscarProdutosAPI);

    // Ao digitar (Espera 400ms e busca sozinho em tempo real)
    inputFiltroGrid?.addEventListener('input', (e) => {
        clearTimeout(timeoutBuscaGrid);
        timeoutBuscaGrid = setTimeout(() => {
            buscarProdutosAPI();
        }, 400); // 400ms de delay para não sobrecarregar o servidor
    });

    // Mantém o Enter para busca super imediata caso o usuário seja muito rápido
    inputFiltroGrid?.addEventListener('keyup', (e) => {
        if (e.key === 'Enter') {
            clearTimeout(timeoutBuscaGrid);
            buscarProdutosAPI();
        }
    });

    // Form Submit (Salvar/Editar)
    document.getElementById('formSalvarProduto')?.addEventListener('submit', handleSalvarProduto);
}

function navegarProdutos(tela) {
    const views = {
        menu: document.getElementById('conteudoMenuProdutos'),
        cadastro: document.getElementById('conteudoFormProdutos'),
        consulta: document.getElementById('conteudoConsultaProdutos'),
        prodHistorico: document.getElementById('prodViewHistorico'),
        prodForm: document.getElementById('prodViewForm')
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
    } else if (tela === 'prodHistorico') {
        views.prodHistorico.style.display = 'block';
        titulo.innerText = "Produtos > Histórico de Produções";
        btnVoltar.style.display = 'flex';
        icone.style.display = 'none';
    } else if (tela === 'prodForm') {
        views.prodForm.style.display = 'block';
        titulo.innerText = "Produtos > Nova Produção";
        btnVoltar.style.display = 'flex';
        icone.style.display = 'none';
        prodResetarFormulario();
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

const REFERENCIAS_POR_UNIDADE = {
    PESO:        [['MILIGRAMA','Miligrama (mg)'],['GRAMA','Grama (g)'],['QUILOGRAMA','Quilograma (kg)'],['TONELADA','Tonelada (t)']],
    VOLUME:      [['MILILITRO','Mililitro (ml)'],['LITRO','Litro (L)']],
    COMPRIMENTO: [['MILIMETRO','Milímetro (mm)'],['CENTIMETRO','Centímetro (cm)'],['METRO','Metro (m)'],['QUILOMETRO','Quilômetro (km)']],
    AREA:        [['CENTIMETRO_QUADRADO','Centímetro² (cm²)'],['METRO_QUADRADO','Metro² (m²)'],['METRO_CUBICO','Metro³ (m³)']],
    UNIDADE:     [['UNIDADE','Unidade (un)'],['DUZIA','Dúzia (dz)'],['CENTO','Cento (ct)']]
};

function handleUnidadeMedidaChange() {
    const unidade = document.getElementById('produtoUnidadeMedida').value;
    const selRef = document.getElementById('produtoReferencia');
    selRef.innerHTML = '<option value="">-- Selecione --</option>';

    if (unidade && REFERENCIAS_POR_UNIDADE[unidade]) {
        REFERENCIAS_POR_UNIDADE[unidade].forEach(([val, label]) => {
            const opt = document.createElement('option');
            opt.value = val;
            opt.textContent = label;
            selRef.appendChild(opt);
        });
        selRef.disabled = false;
    } else {
        selRef.innerHTML = '<option value="">-- Selecione a unidade primeiro --</option>';
        selRef.disabled = true;
    }
}

function handleTipoProdutoChange() {
    const tipo = document.getElementById('produtoTipo').value;
    const secao = document.getElementById('secaoMateriasPrimas');
    const inputValor = document.getElementById('produtoValor');
    const precisaMP = tipo === 'PF' || tipo === 'MPPF';
    const valorObrigatorio = tipo === 'PF' || tipo === 'R';

    inputValor.required = valorObrigatorio;
    inputValor.placeholder = valorObrigatorio ? 'R$ 0,00 *' : 'R$ 0,00 (opcional)';

    const inputEstoque = document.getElementById('produtoQuantidade');
    inputEstoque.disabled = false;
    inputEstoque.title = '';

    if (precisaMP) {
        secao.style.display = 'block';
        carregarMateriaisDisponiveisNoForm();
    } else {
        secao.style.display = 'none';
    }
}

async function carregarMateriaisDisponiveisNoForm(quantidadesPorId = {}) {
    const lista = document.getElementById('listaMateriasPrimasDisp');
    lista.innerHTML = '<span style="font-size:12px; color:var(--text-muted); padding:8px; display:block; text-align:center;"><i class="ph ph-spinner ph-spin"></i> Carregando...</span>';

    try {
        const res = await fetch(`${API_URL}/api/produtos/materias-primas-disponiveis`, {
            method: 'GET', headers: getAuthHeader()
        });
        if (!res.ok) throw new Error();

        const mps = await res.json();
        const produtoAtualId = document.getElementById('produtoId').value;
        const disponiveis = mps.filter(mp => String(mp.codProduto) !== String(produtoAtualId));

        if (disponiveis.length === 0) {
            lista.innerHTML = '<span style="font-size:12px; color:var(--text-muted); padding:8px; display:block; text-align:center;">Nenhuma matéria-prima cadastrada. Crie produtos do tipo MP primeiro.</span>';
            return;
        }

        lista.innerHTML = '';
        disponiveis.forEach(mp => {
            const id = mp.codProduto;
            const qtdSalva = quantidadesPorId[id] || '';
            const checked = qtdSalva !== '' ? 'checked' : '';

            const row = document.createElement('div');
            row.className = 'composicao-item';
            row.innerHTML = `
                <label>
                    <input type="checkbox" name="mpSelecionada" value="${id}" ${checked}
                        onchange="atualizarContadorMp(); toggleQtdMp(this)">
                    <span class="mp-nome">${mp.nome}</span>
                    <span class="mp-tipo">${mp.tipo || 'MP'}</span>
                </label>
                <input type="number" id="mpQtd_${id}" class="mp-qtd"
                    min="0.001" step="any" placeholder="Qtd"
                    value="${qtdSalva}"
                    style="display:${checked ? 'block' : 'none'};">
            `;
            lista.appendChild(row);
        });

        atualizarContadorMp();
    } catch (e) {
        lista.innerHTML = '<span style="font-size:12px; color:var(--danger); padding:8px; display:block;">Erro ao carregar matérias-primas.</span>';
    }
}

function toggleQtdMp(checkbox) {
    const id = checkbox.value;
    const inputQtd = document.getElementById(`mpQtd_${id}`);
    if (inputQtd) {
        inputQtd.style.display = checkbox.checked ? 'block' : 'none';
        if (!checkbox.checked) inputQtd.value = '';
    }
}

function atualizarContadorMp() {
    const total = document.querySelectorAll('input[name="mpSelecionada"]:checked').length;
    const contador = document.getElementById('contadorMpSelecionadas');
    if (contador) contador.textContent = `${total} selecionada${total !== 1 ? 's' : ''}`;
}

function getMpsComQuantidade() {
    return Array.from(document.querySelectorAll('input[name="mpSelecionada"]:checked'))
        .map(chk => ({
            id: parseInt(chk.value, 10),
            quantidade: parseFloat(document.getElementById(`mpQtd_${chk.value}`)?.value || 1) || 1
        }));
}

async function handleSalvarProduto(e) {
    e.preventDefault();
    const id = document.getElementById('produtoId').value;
    const btn = e.target.querySelector('button[type="submit"]');
    const txtOriginal = btn.innerHTML;
    const tipo = document.getElementById('produtoTipo').value;
    const qtdEstoque = parseInt(document.getElementById('produtoQuantidade').value, 10) || 0;
    const unidadeMedida = document.getElementById('produtoUnidadeMedida').value || null;
    const referencia = document.getElementById('produtoReferencia').value || null;
    const valorCustoRaw = document.getElementById('produtoValorCusto').value;

    const dto = {
        codigo: document.getElementById('produtoCodigo').value || null,
        nome: document.getElementById('produtoNome').value,
        valor: converterMoedaParaFloat(document.getElementById('produtoValor').value),
        valorCusto: valorCustoRaw ? converterMoedaParaFloat(valorCustoRaw) : null,
        quantidadeEstoque: qtdEstoque,
        tipo: tipo,
        unidadeMedida: unidadeMedida,
        referencia: referencia
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

        if (!res.ok) {
            let msg = 'Verifique os dados e tente novamente.';
            try { const body = await res.json(); msg = body.erro || msg; } catch (_) {}
            alert(`Erro ao salvar: ${msg}`);
            return;
        }

        const produtoSalvo = await res.json();
        const idSalvo = produtoSalvo.codProduto || produtoSalvo.id;

        // Vincula matérias-primas se for PF ou MPPF
        if ((tipo === 'PF' || tipo === 'MPPF') && idSalvo) {
            const composicao = getMpsComQuantidade();
            const resMp = await fetch(`${API_URL}/api/produtos/${idSalvo}/materias-primas`, {
                method: 'PUT',
                headers: getAuthHeader(),
                body: JSON.stringify(composicao)
            });
            if (!resMp.ok) {
                let msgErro = `Status ${resMp.status}`;
                try { const body = await resMp.json(); msgErro = body.erro || msgErro; } catch (_) {}
                alert(`Erro ao vincular matérias-primas: ${msgErro}`);
                return;
            }
        }

        navegarProdutos('consulta');
        buscarProdutosAPI();
    } catch (err) {
        alert("Erro de conexão ao salvar.");
    } finally {
        btn.innerHTML = txtOriginal;
        btn.disabled = false;
    }
}

async function prepararEdicaoProduto(id) {
    try {
        const [resProduto, resMps] = await Promise.all([
            fetch(`${API_URL}/api/produtos/${id}`, { method: 'GET', headers: getAuthHeader() }),
            fetch(`${API_URL}/api/produtos/${id}/materias-primas`, { method: 'GET', headers: getAuthHeader() })
        ]);

        if (!resProduto.ok) throw new Error('Erro na API');

        const produto = await resProduto.json();
        const composicao = resMps.ok ? await resMps.json() : [];

        // Monta mapa { codProduto: quantidade } a partir da composição
        const quantidadesPorId = {};
        composicao.forEach(entry => {
            if (entry.materiaPrima && entry.materiaPrima.codProduto) {
                quantidadesPorId[entry.materiaPrima.codProduto] = entry.quantidade;
            }
        });

        document.getElementById('produtoId').value = produto.id || produto.codProduto;
        document.getElementById('produtoCodigo').value = produto.codigo || '';
        document.getElementById('produtoNome').value = produto.nome || '';
        document.getElementById('produtoQuantidade').value = produto.quantidadeEstoque || 0;
        document.getElementById('produtoTipo').value = produto.tipo || 'R';
        handleTipoProdutoChange(); // atualiza campo valor obrigatório + seção MPs

        const inputValor = document.getElementById('produtoValor');
        inputValor.value = produto.valor ? (produto.valor * 100).toFixed(0) : '';
        mascaraMoeda(inputValor);

        const inputCusto = document.getElementById('produtoValorCusto');
        inputCusto.value = produto.valorCusto ? (produto.valorCusto * 100).toFixed(0) : '';
        if (inputCusto.value) mascaraMoeda(inputCusto);

        // Unidade de medida e referência
        const selUnidade = document.getElementById('produtoUnidadeMedida');
        selUnidade.value = produto.unidadeMedida || '';
        handleUnidadeMedidaChange();
        if (produto.referencia) {
            document.getElementById('produtoReferencia').value = produto.referencia;
        }

        // Exibe/oculta seção de MPs e carrega com as já vinculadas e suas quantidades
        const tipo = produto.tipo || 'R';
        const secao = document.getElementById('secaoMateriasPrimas');
        if (tipo === 'PF' || tipo === 'MPPF') {
            secao.style.display = 'block';
            await carregarMateriaisDisponiveisNoForm(quantidadesPorId);
        } else {
            secao.style.display = 'none';
        }

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
            if (produto.tipo === 'MP') {
                alert(`"${produto.nome}" é uma Matéria-Prima e não pode ser vendida no caixa.`);
                input.value = '';
            } else {
                adicionarAoCarrinhoPDV(produto, 1);
                input.value = '';
            }
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
    document.getElementById('btnAbrirRelContasPagar')?.addEventListener('click', () => {
        navegarRelatorios('contasPagar');
        carregarProdutosParaFiltroCP();
        const hoje = new Date();
        document.getElementById('cpRelVencInicio').valueAsDate = hoje;
        document.getElementById('cpRelVencFim').valueAsDate = hoje;
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
        vendas: document.getElementById('conteudoFormVendas'),
        contasPagar: document.getElementById('conteudoFormContasPagar')
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

async function gerarRelatorioVendasPDF() {
    const dataInicio = document.getElementById('relDataInicio').value;
    const dataFim    = document.getElementById('relDataFim').value;
    const formaPagamento = document.getElementById('relFormaPagamento').value;

    const produtoIds = Array.from(document.querySelectorAll('input[name="prodFiltro"]:checked'))
                           .map(c => Number(c.value));

    if (!dataInicio || !dataFim) { alert("Selecione o período."); return; }
    if (produtoIds.length === 0) { alert("Selecione pelo menos um produto."); return; }

    const btn = document.querySelector('#formRelatorioVendas button[type="submit"]');
    const txtOriginal = btn ? btn.innerHTML : 'Gerar';
    if (btn) { btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Processando...'; btn.disabled = true; }

    try {
        const res = await fetch(`${API_URL}/api/relatorios/vendas`, {
            method: 'POST',
            headers: { ...getAuthHeader(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ dataInicio, dataFim, formaPagamento, produtoIds })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({ erro: 'Erro desconhecido' }));
            alert(err.erro || 'Erro ao gerar relatório.');
            return;
        }

        const blob = await res.blob();
        const url  = window.URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => window.URL.revokeObjectURL(url), 10000);

    } catch (e) {
        console.error(e);
        alert("Erro ao gerar relatório: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = txtOriginal; btn.disabled = false; }
    }
}

// ── ESQUEMA DE PRODUÇÕES ───────────────────────────────────────────────────────

// Abre uma aba IMEDIATAMENTE (ainda no gesto de clique) com uma tela de carregamento.
// Necessário porque chamar window.open() depois de um await faz o navegador bloquear o pop-up.
function abrirAbaCarregando() {
    const aba = window.open('', '_blank');
    if (aba) {
        aba.document.write('<!DOCTYPE html><html><head><meta charset="UTF-8"><title>Carregando…</title></head>'
            + '<body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;'
            + 'background:#0f172a;color:#94a3b8;font-family:-apple-system,Segoe UI,sans-serif;font-size:14px;">'
            + '<i>Carregando…</i></body></html>');
    }
    return aba;
}

// Navega a aba já aberta para o HTML final (via blob). Faz fallback se o pop-up foi bloqueado.
function renderizarNaAba(aba, html) {
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url  = window.URL.createObjectURL(blob);
    if (aba) {
        aba.location.href = url;
    } else {
        const fallback = window.open(url, '_blank');
        if (!fallback) {
            window.URL.revokeObjectURL(url);
            alert('Seu navegador bloqueou a nova aba. Permita pop-ups para este site e tente novamente.');
            return;
        }
    }
    setTimeout(() => window.URL.revokeObjectURL(url), 15000);
}

async function abrirEsquemaProducoes() {
    const btn = document.getElementById('btnEsquemaProducoes');
    const txtOriginal = btn ? btn.innerHTML : '';
    if (btn) { btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i><span>Carregando...</span>'; btn.disabled = true; }

    // Abre a aba já no clique para não ser bloqueada (o conteúdo só fica pronto após o await)
    const aba = abrirAbaCarregando();

    try {
        const res = await fetch(`${API_URL}/api/produtos/esquema`, { headers: getAuthHeader() });
        if (!res.ok) throw new Error('Erro ao buscar esquema de produções.');
        const treeData = await res.json();

        const totalNos = contarNos(treeData) - 1; // -1 para excluir raiz virtual

        const htmlDiagrama = gerarHtmlDiagrama(treeData, totalNos);
        renderizarNaAba(aba, htmlDiagrama);

    } catch (e) {
        if (aba) aba.close();
        alert('Erro ao abrir esquema: ' + e.message);
    } finally {
        if (btn) { btn.innerHTML = txtOriginal; btn.disabled = false; }
    }
}

function contarNos(node) {
    if (!node) return 0;
    let total = 1;
    if (node.children) node.children.forEach(c => total += contarNos(c));
    return total;
}

function gerarHtmlDiagrama(treeData, totalNos) {
    const dataJson = JSON.stringify(treeData);
    return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<title>Esquema de Produções — Horus</title>
<script src="https://d3js.org/d3.v7.min.js"><\/script>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:#0f172a; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif; overflow:hidden; }
  svg { width:100vw; height:100vh; display:block; }

  .link { fill:none; stroke:#334155; stroke-width:1.5px; }
  .link-label { fill:#64748b; font-size:10px; font-family:inherit; }

  .node-rect { rx:8; ry:8; }
  .node-nome { fill:#f1f5f9; font-size:12px; font-weight:600; font-family:inherit; }
  .node-sub  { fill:#94a3b8; font-size:10px; font-family:inherit; }
  .node-root > rect { fill:#1e293b !important; stroke:#334155; stroke-width:1.5; }
  .node-root text { fill:#94a3b8 !important; font-size:13px !important; font-weight:700 !important; }

  .node-collapsed > rect { stroke:#fbbf24 !important; stroke-width:2px !important; }

  #hud { position:fixed; top:0; left:0; right:0; display:flex; justify-content:space-between; align-items:center;
         padding:14px 20px; background:rgba(15,23,42,0.92); border-bottom:1px solid #1e293b; backdrop-filter:blur(8px); }
  #hud-left h1 { font-size:15px; font-weight:700; color:#f1f5f9; }
  #hud-left p  { font-size:11px; color:#64748b; margin-top:2px; }
  #hud-right   { display:flex; gap:8px; }

  .hud-btn { background:#1e293b; border:1px solid #334155; color:#cbd5e1; padding:7px 14px;
             border-radius:6px; cursor:pointer; font-size:12px; font-family:inherit;
             transition:background 0.15s; }
  .hud-btn:hover { background:#334155; color:#f1f5f9; }

  #legend { position:fixed; bottom:20px; left:20px; background:rgba(15,23,42,0.9);
            border:1px solid #1e293b; border-radius:10px; padding:14px 18px; }
  .leg-row { display:flex; align-items:center; gap:8px; margin-bottom:5px; font-size:11px; color:#94a3b8; }
  .leg-row:last-child { margin-bottom:0; }
  .leg-dot { width:10px; height:10px; border-radius:3px; flex-shrink:0; }

  #hint { position:fixed; bottom:20px; right:20px; font-size:11px; color:#475569;
          background:rgba(15,23,42,0.85); border:1px solid #1e293b; border-radius:8px; padding:10px 14px; }
</style>
</head>
<body>

<div id="hud">
  <div id="hud-left">
    <h1>Esquema de Produções</h1>
    <p id="hud-sub">Sistema de Gestão Horus &nbsp;·&nbsp; ${totalNos} produtos mapeados</p>
  </div>
  <div id="hud-right">
    <button class="hud-btn" id="btnOrient" onclick="toggleOrientacao()">&#8646; Vertical</button>
    <button class="hud-btn" onclick="expandAll()">&#43; Expandir Tudo</button>
    <button class="hud-btn" onclick="collapseAll()">&#8722; Recolher</button>
    <button class="hud-btn" onclick="resetView()">&#8635; Centralizar</button>
  </div>
</div>

<svg id="svg"></svg>

<div id="legend">
  <div class="leg-row"><div class="leg-dot" style="background:#3b82f6"></div>MPPF — Mat. Prima / Produto Final</div>
  <div class="leg-row"><div class="leg-dot" style="background:#10b981"></div>PF — Produto Final</div>
  <div class="leg-row"><div class="leg-dot" style="background:#6b7280"></div>MP — Matéria Prima</div>
  <div class="leg-row"><div class="leg-dot" style="background:#f97316"></div>R — Revenda</div>
</div>

<div id="hint">Scroll para zoom · Arrastar para mover · Clique no nó para expandir/recolher</div>

<script>
const RAW = ${dataJson};

const COLORS = { MP:'#6b7280', PF:'#10b981', MPPF:'#3b82f6', R:'#f97316', ROOT:'#0f172a' };
const TIPO_LABEL = { MP:'Matéria Prima', PF:'Produto Final', MPPF:'MP / Prod. Final', R:'Revenda' };

const NW = 170, NH = 62, GAP_X = 90, GAP_Y = 28;

const svgEl = document.getElementById('svg');
const W = window.innerWidth, H = window.innerHeight;
svgEl.setAttribute('viewBox', \`0 0 \${W} \${H}\`);

const svg = d3.select('#svg');
const gMain = svg.append('g');
const gLinks = gMain.append('g');
const gNodes = gMain.append('g');

// Zoom
const zoom = d3.zoom().scaleExtent([0.05, 3])
  .on('zoom', e => gMain.attr('transform', e.transform));
svg.call(zoom);

// Hierarquia
const root = d3.hierarchy(RAW);
root.x0 = 0; root.y0 = 0;

// Colapsar nós além da profundidade 2 para não sobrecarregar a tela inicial
root.descendants().forEach(d => {
  if (d.depth >= 2 && d.children) { d._children = d.children; d.children = null; }
});

// Orientação: true = horizontal (esquerda → direita), false = vertical (cima → baixo)
let HORIZ = true;

function treeLayout(r) {
  const layout = HORIZ
    ? d3.tree().nodeSize([NH + GAP_Y, NW + GAP_X])   // profundidade no eixo X
    : d3.tree().nodeSize([NW + GAP_Y + 30, NH + GAP_X]); // profundidade no eixo Y
  layout(r);
}

// Converte coordenadas do layout para a tela conforme a orientação
function px(d) { return HORIZ ? d.y : d.x; }
function py(d) { return HORIZ ? d.x : d.y; }

function toggleOrientacao() {
  HORIZ = !HORIZ;
  document.getElementById('btnOrient').innerHTML = HORIZ ? '&#8646; Vertical' : '&#8646; Horizontal';
  update(root);
  resetView();
}

let uid = 0;
function update(src) {
  treeLayout(root);

  const nodes = root.descendants();
  const links = root.links();
  const dur = 350;

  // ── Nós ──────────────────────────────────────────────────────────────
  const nodeSel = gNodes.selectAll('g.node').data(nodes, d => d.uid || (d.uid = ++uid));

  const nodeEnter = nodeSel.enter().append('g').attr('class', 'node')
    .attr('transform', \`translate(\${px({x:src.x0??0,y:src.y0??0})},\${py({x:src.x0??0,y:src.y0??0})})\`)
    .style('cursor', d => (d.children || d._children) ? 'pointer' : 'default')
    .on('click', (ev, d) => { toggleNode(d); update(d); });

  nodeEnter.each(function(d) {
    const g = d3.select(this);
    if (d.data.tipo === 'ROOT') {
      g.classed('node-root', true);
      g.append('rect').attr('class','node-rect')
        .attr('x', -NW/2).attr('y', -NH/2)
        .attr('width', NW).attr('height', NH).attr('rx', 10);
      g.append('text').attr('class','node-nome').attr('text-anchor','middle').attr('dy','0.35em')
        .text('Todos os Produtos');
    } else {
      const col = COLORS[d.data.tipo] || '#475569';
      g.append('rect').attr('class','node-rect')
        .attr('x', -NW/2).attr('y', -NH/2)
        .attr('width', NW).attr('height', NH).attr('rx', 8)
        .attr('fill', col);
      // faixa de tipo no topo — path com cantos arredondados apenas em cima
      const r = 8, hh = 20, hw = NW / 2;
      const hPath = \`M\${-hw + r},\${-NH/2} h\${NW - 2*r} a\${r},\${r} 0 0 1 \${r},\${r}
                     v\${hh - r} h\${-NW} v\${-(hh - r)} a\${r},\${r} 0 0 1 \${r},\${-r}z\`;
      g.append('path').attr('d', hPath).attr('fill', 'rgba(0,0,0,0.22)');
      // tipo
      g.append('text').attr('class','node-sub').attr('text-anchor','middle').attr('dy', -NH/2 + 13)
        .text(TIPO_LABEL[d.data.tipo] || d.data.tipo);
      // nome
      g.append('text').attr('class','node-nome').attr('text-anchor','middle').attr('dy','4')
        .text(trunc(d.data.nome, 22));
      // estoque
      g.append('text').attr('class','node-sub').attr('text-anchor','middle').attr('dy', NH/2 - 8)
        .text('Estoque: ' + (d.data.estoque ?? 0));
    }
  });

  // Merge enter + update
  const nodeAll = nodeEnter.merge(nodeSel);
  nodeAll.transition().duration(dur).attr('transform', d => \`translate(\${px(d)},\${py(d)})\`);
  // borda amarela se colapsado
  nodeAll.classed('node-collapsed', d => !!d._children);

  // Exit
  nodeSel.exit().transition().duration(dur)
    .attr('transform', \`translate(\${px(src)},\${py(src)})\`).remove();

  // ── Links ─────────────────────────────────────────────────────────────
  const linkSel = gLinks.selectAll('path.link').data(links, d => d.target.uid);

  linkSel.enter().insert('path','g').attr('class','link')
    .attr('d', () => bezier({ x: src.x0 ?? 0, y: src.y0 ?? 0 }, { x: src.x0 ?? 0, y: src.y0 ?? 0 }))
    .merge(linkSel).transition().duration(dur)
    .attr('d', d => bezier(d.source, d.target));

  linkSel.exit().transition().duration(dur)
    .attr('d', bezier({ x: src.x, y: src.y }, { x: src.x, y: src.y })).remove();

  // ── Rótulos de quantidade nas arestas ─────────────────────────────────
  const lblSel = gLinks.selectAll('text.link-label')
    .data(links.filter(d => d.target.data.quantidade != null), d => d.target.uid + '_q');

  lblSel.enter().append('text').attr('class','link-label')
    .attr('text-anchor','middle')
    .merge(lblSel).transition().duration(dur)
    .attr('x', d => (px(d.source) + px(d.target)) / 2)
    .attr('y', d => (py(d.source) + py(d.target)) / 2 - 5)
    .text(d => '×' + fmt(d.target.data.quantidade));

  lblSel.exit().remove();

  // Salva posições para próxima transição
  nodes.forEach(d => { d.x0 = d.x; d.y0 = d.y; });
}

function bezier(s, t) {
  if (HORIZ) {
    const mx = (px(s) + px(t)) / 2;
    return \`M\${px(s)},\${py(s)} C\${mx},\${py(s)} \${mx},\${py(t)} \${px(t)},\${py(t)}\`;
  }
  const my = (py(s) + py(t)) / 2;
  return \`M\${px(s)},\${py(s)} C\${px(s)},\${my} \${px(t)},\${my} \${px(t)},\${py(t)}\`;
}

function toggleNode(d) {
  if (d.children) { d._children = d.children; d.children = null; }
  else             { d.children = d._children; d._children = null; }
}

function expandAll() {
  // Percorre recursivamente incluindo _children (não acessíveis via descendants())
  function expandRec(d) {
    if (d._children) { d.children = d._children; d._children = null; }
    if (d.children) d.children.forEach(expandRec);
  }
  expandRec(root);
  update(root);
}

function collapseAll() {
  // Percorre recursivamente incluindo _children para garantir colapso total
  function collapseRec(d) {
    if (d.children) { d.children.forEach(collapseRec); }
    if (d._children) { d._children.forEach(collapseRec); }
    if (d.depth > 0 && d.children) { d._children = d.children; d.children = null; }
  }
  collapseRec(root);
  update(root);
}

function resetView() {
  const t = HORIZ
    ? d3.zoomIdentity.translate(160, H / 2)
    : d3.zoomIdentity.translate(W / 2, 120);
  svg.transition().duration(500).call(zoom.transform, t);
}

function trunc(s, n) { return s && s.length > n ? s.slice(0, n) + '…' : (s || '—'); }
function fmt(v) { return v != null ? (Number.isInteger(+v) ? v : (+v).toFixed(2)) : ''; }

// Render inicial + centralização
update(root);
setTimeout(resetView, 100);
<\/script>
</body>
</html>`;
}

// ── RELATÓRIO DE CONTAS A PAGAR ────────────────────────────────────────────────

async function carregarProdutosParaFiltroCP() {
    const divLista = document.getElementById('listaProdutosCheckCP');
    divLista.innerHTML = '<span style="font-size:12px; color:var(--text-muted);">Carregando produtos...</span>';
    try {
        const res = await fetch(`${API_URL}/api/produtos`, { method: 'GET', headers: getAuthHeader() });
        if (!res.ok) throw new Error();
        const produtos = await res.json();
        divLista.innerHTML = produtos.length === 0 ? '<span style="font-size:12px">Nenhum produto cadastrado.</span>' : '';
        produtos.forEach(prod => {
            const id = prod.id || prod.codProduto;
            divLista.innerHTML += `
                <div style="display:flex; align-items:center; margin-bottom:8px; border-bottom:1px solid var(--border-subtle); padding-bottom:4px;">
                    <input type="checkbox" name="prodFiltroCP" value="${id}" id="chkCP_${id}" checked style="margin-right:8px;">
                    <label for="chkCP_${id}" style="font-size:13px; cursor:pointer;">${prod.nome}</label>
                </div>`;
        });
    } catch (e) {
        divLista.innerHTML = '<span style="color:var(--danger); font-size:12px">Erro ao buscar dados.</span>';
    }
}

function marcarTodosProdutosCP(marcar) {
    document.querySelectorAll('input[name="prodFiltroCP"]').forEach(chk => chk.checked = marcar);
}

async function gerarRelatorioContasPagarPDF() {
    const vencInicio = document.getElementById('cpRelVencInicio').value;
    const vencFim    = document.getElementById('cpRelVencFim').value;
    const status     = document.getElementById('cpRelStatus').value;

    const produtoIds = Array.from(document.querySelectorAll('input[name="prodFiltroCP"]:checked'))
                           .map(c => Number(c.value));

    if (!vencInicio || !vencFim) { alert("Selecione o período de vencimento."); return; }
    if (produtoIds.length === 0) { alert("Selecione pelo menos um produto."); return; }

    const btn = document.querySelector('#formRelatorioContasPagar button[type="submit"]');
    const txtOriginal = btn ? btn.innerHTML : 'Gerar';
    if (btn) { btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Processando...'; btn.disabled = true; }

    try {
        const res = await fetch(`${API_URL}/api/relatorios/contas-pagar`, {
            method: 'POST',
            headers: { ...getAuthHeader(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ vencInicio, vencFim, status, produtoIds })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({ erro: 'Erro desconhecido' }));
            alert(err.erro || 'Erro ao gerar relatório.');
            return;
        }

        const blob = await res.blob();
        const url  = window.URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => window.URL.revokeObjectURL(url), 10000);

    } catch (e) {
        console.error(e);
        alert("Erro ao gerar relatório: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = txtOriginal; btn.disabled = false; }
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
    // Dashboard agora abre em nova aba com tema noturno (mesmo padrão do esquema de produções)
    document.getElementById('dashboardBtn')?.addEventListener('click', abrirDashboardNoturno);
}

async function abrirDashboardNoturno() {
    const btnIcon = document.querySelector('#dashboardBtn i');
    if (btnIcon) btnIcon.className = 'ph ph-spinner ph-spin';

    // Abre a aba já no clique para não ser bloqueada (o conteúdo só fica pronto após o await)
    const aba = abrirAbaCarregando();

    try {
        const [resVendas, resContas, resAnalise] = await Promise.all([
            fetch(`${API_URL}/api/vendas`,                  { headers: getAuthHeader() }),
            fetch(`${API_URL}/api/contas-pagar`,            { headers: getAuthHeader() }),
            fetch(`${API_URL}/api/produtos/analise-lucro`,  { headers: getAuthHeader() })
        ]);
        if (!resVendas.ok || !resContas.ok || !resAnalise.ok) throw new Error('Erro ao buscar dados do dashboard.');

        const vendas  = await resVendas.json();
        const contas  = await resContas.json();
        const analise = await resAnalise.json();

        const empresaNome = localStorage.getItem('horus_empresa_nome') || 'Horus';
        const html = gerarHtmlDashboardNoturno(vendas, contas, analise, empresaNome);
        renderizarNaAba(aba, html);

    } catch (e) {
        if (aba) aba.close();
        console.error(e);
        alert('Erro ao abrir o dashboard: ' + e.message);
    } finally {
        if (btnIcon) btnIcon.className = 'ph ph-squares-four';
    }
}

function gerarHtmlDashboardNoturno(vendas, contas, analise, empresaNome) {
    return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<title>Dashboard Gerencial — Horus</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js"><\/script>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:#0f172a; color:#f1f5f9; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
         min-height:100vh; padding:84px 24px 32px; }

  #hud { position:fixed; top:0; left:0; right:0; z-index:50; display:flex; justify-content:space-between; align-items:center;
         padding:14px 24px; background:rgba(15,23,42,0.92); border-bottom:1px solid #1e293b; backdrop-filter:blur(8px); }
  #hud h1 { font-size:15px; font-weight:700; }
  #hud p  { font-size:11px; color:#64748b; margin-top:2px; }
  #hud select, #hud input[type="date"] {
    background:#1e293b; border:1px solid #334155; color:#cbd5e1; padding:7px 12px;
    border-radius:6px; font-size:12px; font-family:inherit; cursor:pointer; outline:none;
  }
  #hud input[type="date"] { color-scheme:dark; }
  #hud select:focus, #hud input[type="date"]:focus { border-color:#6366f1; }

  .wrap { max-width:1280px; margin:0 auto; }
  .kpis { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; margin-bottom:18px; }
  .kpi  { background:#1e293b; border:1px solid #283548; border-radius:12px; padding:16px 18px; position:relative; overflow:hidden; }
  .kpi::after { content:''; position:absolute; left:0; top:0; bottom:0; width:3px; background:var(--bar,#6366f1); }
  .kpi .lbl { font-size:10px; font-weight:700; color:#64748b; text-transform:uppercase; letter-spacing:0.07em; }
  .kpi .val { font-size:21px; font-weight:800; margin-top:4px; letter-spacing:-0.02em; }
  .kpi .sub { font-size:11px; color:#64748b; margin-top:2px; }

  .grid2 { display:grid; grid-template-columns:2fr 1fr; gap:12px; margin-bottom:18px; }
  @media (max-width:900px){ .grid2 { grid-template-columns:1fr; } }
  .card { background:#1e293b; border:1px solid #283548; border-radius:12px; padding:18px; }
  .card h3 { font-size:12px; font-weight:700; color:#cbd5e1; margin-bottom:14px; display:flex; align-items:center; gap:8px; }
  .card h3::before { content:''; width:4px; height:14px; border-radius:4px; background:linear-gradient(180deg,#6366f1,#8b5cf6); }
  .ch { position:relative; height:260px; }

  table { width:100%; border-collapse:collapse; font-size:12.5px; }
  th { text-align:left; font-size:10px; font-weight:700; color:#64748b; text-transform:uppercase; letter-spacing:0.07em;
       padding:9px 12px; border-bottom:1px solid #283548; }
  td { padding:10px 12px; border-bottom:1px solid #243044; }
  tr:last-child td { border-bottom:none; }
  tr:hover td { background:rgba(99,102,241,0.05); }
  .num { text-align:right; font-variant-numeric:tabular-nums; }
  .pos { color:#34d399; font-weight:700; }
  .neg { color:#f87171; font-weight:700; }
  .tag { display:inline-block; font-size:9.5px; font-weight:700; padding:2px 8px; border-radius:20px; }
  .tag.MP   { background:rgba(107,114,128,0.22); color:#9ca3af; }
  .tag.PF   { background:rgba(16,185,129,0.18);  color:#34d399; }
  .tag.MPPF { background:rgba(59,130,246,0.18);  color:#60a5fa; }
  .tag.R    { background:rgba(249,115,22,0.18);  color:#fb923c; }
  .rankpos  { font-weight:800; color:#64748b; width:38px; }
  .rankpos.top { color:#fbbf24; }
  .lucrobar { height:5px; border-radius:4px; background:#283548; margin-top:5px; overflow:hidden; }
  .lucrobar > div { height:100%; border-radius:4px; }
</style>
</head>
<body>

<div id="hud">
  <div>
    <h1>Dashboard Gerencial</h1>
    <p>${empresaNome} &nbsp;·&nbsp; Sistema de Gestão Horus</p>
  </div>
  <div style="display:flex; align-items:center; gap:8px;">
    <div id="periodoCustom" style="display:none; align-items:center; gap:6px;">
      <input type="date" id="dataIni" onchange="render()">
      <span style="color:#64748b; font-size:11px;">até</span>
      <input type="date" id="dataFim" onchange="render()">
    </div>
    <select id="filtroPeriodo" onchange="onPeriodoChange()">
      <option value="7">Últimos 7 dias</option>
      <option value="30" selected>Últimos 30 dias</option>
      <option value="90">Últimos 90 dias</option>
      <option value="365">Último ano</option>
      <option value="0">Todo o período</option>
      <option value="CUSTOM">Período personalizado</option>
    </select>
  </div>
</div>

<div class="wrap">
  <div class="kpis">
    <div class="kpi" style="--bar:#6366f1"><div class="lbl">Receita</div><div class="val" id="kReceita">—</div><div class="sub" id="kReceitaSub"></div></div>
    <div class="kpi" style="--bar:#8b5cf6"><div class="lbl">Ticket Médio</div><div class="val" id="kTicket">—</div></div>
    <div class="kpi" style="--bar:#34d399"><div class="lbl">Vendas</div><div class="val" id="kVolume">—</div></div>
    <div class="kpi" style="--bar:#fbbf24"><div class="lbl">Contas em Aberto</div><div class="val" id="kCpAberto">—</div><div class="sub" id="kCpAbertoSub"></div></div>
    <div class="kpi" style="--bar:#f87171"><div class="lbl">Contas Vencidas</div><div class="val" id="kCpVencido">—</div><div class="sub" id="kCpVencidoSub"></div></div>
    <div class="kpi" style="--bar:#10b981"><div class="lbl">Contas Pagas</div><div class="val" id="kCpPago">—</div></div>
  </div>

  <div class="grid2">
    <div class="card"><h3>Receita por dia</h3><div class="ch"><canvas id="chReceita"></canvas></div></div>
    <div class="card"><h3>Top produtos vendidos</h3><div class="ch"><canvas id="chProdutos"></canvas></div></div>
  </div>

  <div class="card">
    <h3>Ranking de lucratividade — custo de produção × valor de venda (por unidade)</h3>
    <table>
      <thead><tr>
        <th></th><th>Produto</th><th>Tipo</th>
        <th class="num">Custo Produção</th><th class="num">Valor Venda</th>
        <th class="num">Lucro Unitário</th><th class="num">Margem</th>
      </tr></thead>
      <tbody id="tbodyRanking"></tbody>
    </table>
  </div>
</div>

<script>
const VENDAS  = ${JSON.stringify(vendas)};
const CONTAS  = ${JSON.stringify(contas)};
const ANALISE = ${JSON.stringify(analise)};

const moeda = v => (v ?? 0).toLocaleString('pt-BR', { style:'currency', currency:'BRL' });
let chR = null, chP = null;

function onPeriodoChange() {
  const custom = document.getElementById('filtroPeriodo').value === 'CUSTOM';
  document.getElementById('periodoCustom').style.display = custom ? 'flex' : 'none';
  if (custom && !document.getElementById('dataIni').value) {
    const hojeStr = new Date().toISOString().split('T')[0];
    document.getElementById('dataIni').value = hojeStr;
    document.getElementById('dataFim').value = hojeStr;
  }
  render();
}

function render() {
  const filtro = document.getElementById('filtroPeriodo').value;
  let fim = new Date(); fim.setHours(23,59,59,999);
  let ini = new Date(); ini.setHours(0,0,0,0);

  if (filtro === 'CUSTOM') {
    const vIni = document.getElementById('dataIni').value;
    const vFim = document.getElementById('dataFim').value;
    if (vIni) ini = new Date(vIni + 'T00:00:00');
    if (vFim) fim = new Date(vFim + 'T23:59:59');
  } else {
    const dias = parseInt(filtro, 10);
    if (dias > 0) ini.setDate(ini.getDate() - dias); else ini.setFullYear(2000);
  }

  // ── Vendas ────────────────────────────────────────────────────────────
  const vendas = VENDAS.filter(v => { const d = new Date(v.dataVenda); return d >= ini && d <= fim; });
  let receita = 0; const porDia = {}; const porProduto = {};
  vendas.forEach(v => {
    receita += v.valorTotal;
    const dt = new Date(v.dataVenda).toLocaleDateString('pt-BR', { day:'2-digit', month:'2-digit' });
    porDia[dt] = (porDia[dt] || 0) + v.valorTotal;
    (v.itens || []).forEach(i => {
      const n = i.nome || i.produto?.nome || '—';
      porProduto[n] = (porProduto[n] || 0) + i.quantidade;
    });
  });
  document.getElementById('kReceita').innerText = moeda(receita);
  document.getElementById('kReceitaSub').innerText =
    filtro === 'CUSTOM' ? ini.toLocaleDateString('pt-BR') + ' a ' + fim.toLocaleDateString('pt-BR')
    : filtro === '0'    ? 'todo o período'
    : 'últimos ' + filtro + ' dias';
  document.getElementById('kTicket').innerText = moeda(vendas.length ? receita / vendas.length : 0);
  document.getElementById('kVolume').innerText = vendas.length;

  // ── Contas a Pagar (sempre posição atual, independe do filtro) ───────
  const hoje = new Date(); hoje.setHours(0,0,0,0);
  let cpAberto = 0, cpVencido = 0, cpPago = 0, nAberto = 0, nVencido = 0;
  CONTAS.forEach(c => {
    const parcelas = (c.parcelas && c.parcelas.length) ? c.parcelas
        : [{ valorParcela: c.valorTotal, dataVencimento: c.dataVencimento, paga: c.paga }];
    parcelas.forEach(p => {
      const val = p.valorParcela ?? 0;
      if (p.paga) { cpPago += val; return; }
      cpAberto += val; nAberto++;
      const venc = p.dataVencimento ? new Date(p.dataVencimento + 'T00:00:00') : null;
      if (venc && venc < hoje) { cpVencido += val; nVencido++; }
    });
  });
  document.getElementById('kCpAberto').innerText  = moeda(cpAberto);
  document.getElementById('kCpAbertoSub').innerText  = nAberto + ' parcela(s)';
  document.getElementById('kCpVencido').innerText = moeda(cpVencido);
  document.getElementById('kCpVencidoSub').innerText = nVencido + ' parcela(s) vencida(s)';
  document.getElementById('kCpPago').innerText    = moeda(cpPago);

  // ── Gráficos ──────────────────────────────────────────────────────────
  const lbl = Object.keys(porDia).reverse(), dados = Object.values(porDia).reverse();
  if (chR) chR.destroy();
  const ctxR = document.getElementById('chReceita').getContext('2d');
  const grad = ctxR.createLinearGradient(0, 0, 0, 260);
  grad.addColorStop(0, 'rgba(99,102,241,0.45)'); grad.addColorStop(1, 'rgba(99,102,241,0.02)');
  chR = new Chart(ctxR, {
    type:'line',
    data:{ labels:lbl, datasets:[{ data:dados, backgroundColor:grad, borderColor:'#818cf8', borderWidth:2,
           pointBackgroundColor:'#0f172a', pointBorderColor:'#818cf8', pointRadius:3.5, fill:true, tension:0.35 }] },
    options:{ responsive:true, maintainAspectRatio:false, plugins:{ legend:{ display:false } },
      scales:{ x:{ ticks:{ color:'#64748b' }, grid:{ color:'#243044' } },
               y:{ beginAtZero:true, ticks:{ color:'#64748b', callback:v => 'R$ ' + v }, grid:{ color:'#243044' } } } }
  });

  const top = Object.entries(porProduto).sort((a,b) => b[1]-a[1]).slice(0,5);
  if (chP) chP.destroy();
  chP = new Chart(document.getElementById('chProdutos'), {
    type:'doughnut',
    data:{ labels: top.map(t => t[0]), datasets:[{ data: top.map(t => t[1]),
           backgroundColor:['#6366f1','#8b5cf6','#34d399','#fbbf24','#f87171'], borderWidth:0, hoverOffset:5 }] },
    options:{ responsive:true, maintainAspectRatio:false, cutout:'66%',
      plugins:{ legend:{ position:'bottom', labels:{ color:'#94a3b8', usePointStyle:true, boxWidth:8, font:{ size:11 } } } } }
  });
}

// ── Ranking de lucro (estático: posição cadastral atual) ───────────────
function renderRanking() {
  const tbody = document.getElementById('tbodyRanking');
  if (!ANALISE.length) {
    tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#64748b;padding:24px;">Nenhum produto cadastrado.</td></tr>';
    return;
  }
  const maxLucro = Math.max(...ANALISE.map(a => Math.abs(a.lucroUnitario)), 1);
  tbody.innerHTML = ANALISE.map((a, i) => {
    const lucro = a.lucroUnitario ?? 0;
    const cls = lucro >= 0 ? 'pos' : 'neg';
    const cor = lucro >= 0 ? 'linear-gradient(90deg,#10b981,#34d399)' : 'linear-gradient(90deg,#ef4444,#f87171)';
    const larg = Math.max(3, Math.abs(lucro) / maxLucro * 100);
    return \`<tr>
      <td class="rankpos \${i < 3 ? 'top' : ''}">\${i + 1}º</td>
      <td><strong>\${a.nome}</strong>\${a.possuiComposicao ? ' <span style="color:#64748b;font-size:10px;">(produzido)</span>' : ''}
        <div class="lucrobar"><div style="width:\${larg}%;background:\${cor}"></div></div></td>
      <td><span class="tag \${a.tipo || ''}">\${a.tipo || '—'}</span></td>
      <td class="num">\${moeda(a.custoProducao)}</td>
      <td class="num">\${moeda(a.valorVenda)}</td>
      <td class="num \${cls}">\${moeda(lucro)}</td>
      <td class="num \${cls}">\${(a.margem ?? 0).toLocaleString('pt-BR')}%</td>
    </tr>\`;
  }).join('');
}

render();
renderRanking();
<\/script>
</body>
</html>`;
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

/* ==========================================================================
   MÓDULO 9: CONTAS A PAGAR
   ========================================================================== */

/* ── Estado interno do módulo ─────────────────────────────────────── */
let cpItensAdicionados = [];   // [ { codProduto, nome, quantidade, valorUnitario } ]
let cpEtapaAtual = 1;

/* ── Bootstrap ──────────────────────────────────────────────────────── */
function setupContasPagarModule() {
    // Botão sidebar e drag/close já gerenciados pelo setupWindowManagement
    document.getElementById('btnNovaContaPagar')?.addEventListener('click', () => cpNavegar('form'));
    document.getElementById('btnConsultarContasPagar')?.addEventListener('click', () => {
        cpNavegar('consulta');
        cpCarregarContas();
    });
    document.getElementById('btnVoltarContasPagar')?.addEventListener('click', () => cpNavegar('menu'));
    document.getElementById('btnNovaContaPagarConsulta')?.addEventListener('click', () => cpNavegar('form'));
    document.getElementById('btnCpBuscar')?.addEventListener('click', cpFiltrarConsulta);
    document.getElementById('cpFiltroBusca')?.addEventListener('keyup', e => {
        if (e.key === 'Enter') cpFiltrarConsulta();
    });
    document.getElementById('cpBuscaProduto')?.addEventListener('keyup', e => {
        if (e.key === 'Enter') cpBuscarProduto();
    });
}

function abrirJanelaContasPagar() {
    const win = document.getElementById('contasPagarDiv');
    if (!win) return;
    cpNavegar('menu');
    win.style.display = 'flex';
    setTimeout(() => { win.classList.add('is-visible'); bringToFront(win); posicionarJanela(win); }, 10);
}

/* ── Navegação interna ──────────────────────────────────────────────── */
function cpNavegar(tela) {
    ['cpViewMenu', 'cpViewConsulta', 'cpViewForm'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.style.display = 'none';
    });

    const titulo    = document.getElementById('tituloJanelaContasPagar');
    const btnVoltar = document.getElementById('btnVoltarContasPagar');

    if (tela === 'menu') {
        document.getElementById('cpViewMenu').style.display = 'block';
        titulo.innerText = 'Contas a Pagar';
        btnVoltar.style.display = 'none';
    } else if (tela === 'consulta') {
        document.getElementById('cpViewConsulta').style.display = 'block';
        titulo.innerText = 'Contas a Pagar › Consulta';
        btnVoltar.style.display = 'flex';
    } else if (tela === 'form') {
        document.getElementById('cpViewForm').style.display = 'block';
        titulo.innerText = 'Contas a Pagar › Nova Conta';
        btnVoltar.style.display = 'flex';
        cpResetarFormulario();
    }
}

/* ── Consulta ───────────────────────────────────────────────────────── */
async function cpCarregarContas(termo = '') {
    const tbody = document.getElementById('cpTabelaCorpo');
    tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:24px;">
        <i class="ph ph-spinner ph-spin"></i> Carregando...</td></tr>`;

    try {
        const url = termo
            ? `${API_URL}/api/contas-pagar?termo=${encodeURIComponent(termo)}`
            : `${API_URL}/api/contas-pagar`;
        const res = await fetch(url, { headers: getAuthHeader() });
        if (!res.ok) throw new Error('Erro ao buscar contas.');
        const contas = await res.json();
        cpRenderizarConsulta(contas);
        cpRenderizarKpis(contas);
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--danger);padding:24px;">${e.message}</td></tr>`;
    }
}

function cpRenderizarKpis(contas) {
    const kpisEl = document.getElementById('cpKpis');
    if (!kpisEl) return;

    let totalGeral = 0, totalAberto = 0, totalPago = 0;
    contas.forEach(c => {
        const v = Number(c.valorTotal) || 0;
        totalGeral += v;
        if (c.status === 'PAGA') totalPago += v;
        else totalAberto += v;
    });

    kpisEl.innerHTML = `
        <div class="cp-kpi-card kpi-pagar">
            <span class="cp-kpi-label">A Pagar</span>
            <strong class="cp-kpi-value">${formatarMoeda(totalAberto)}</strong>
        </div>
        <div class="cp-kpi-card kpi-pago">
            <span class="cp-kpi-label">Pago</span>
            <strong class="cp-kpi-value">${formatarMoeda(totalPago)}</strong>
        </div>
        <div class="cp-kpi-card kpi-total">
            <span class="cp-kpi-label">Total Cadastrado</span>
            <strong class="cp-kpi-value">${formatarMoeda(totalGeral)}</strong>
        </div>`;
}

function cpRenderizarConsulta(contas) {
    const tbody = document.getElementById('cpTabelaCorpo');
    if (!contas || contas.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:32px;">
            Nenhuma conta cadastrada.</td></tr>`;
        return;
    }

    tbody.innerHTML = contas.map(c => {
        const badge = cpStatusBadge(c.status);
        const venc  = cpFormatarVencimento(c);
        const jaPaga = c.status === 'PAGA';
        const btnPagar = !jaPaga
            ? `<button class="btn-action btn-pagar" onclick="cpMarcarComoPaga(${c.codContaPagar})">
                   <i class="ph ph-check"></i> Pagar
               </button>` : '';
        const desc = (c.descricao || '').replace(/'/g, "\\'");
        return `
        <tr>
            <td>
                <div style="font-weight:600;font-size:13px;">${c.descricao}</div>
                <div style="font-size:11px;color:var(--text-muted);">${c.fornecedor || '—'}</div>
                ${c.numeroNotaFiscal ? `<div style="font-size:10px;color:var(--text-muted);font-family:monospace;">NF: ${c.numeroNotaFiscal}</div>` : ''}
            </td>
            <td class="align-center" style="font-size:12px;">${venc}</td>
            <td class="align-right" style="font-weight:700;">${formatarMoeda(c.valorTotal)}</td>
            <td class="align-center">${badge}</td>
            <td class="align-center">
                <div class="cp-actions-cell">
                    ${btnPagar}
                    <button class="btn-action" onclick="cpEditarConta(${c.codContaPagar})">
                        <i class="ph ph-pencil"></i> Editar
                    </button>
                    <button class="btn-action btn-danger" onclick="cpConfirmarDeletar(${c.codContaPagar}, '${desc}')">
                        <i class="ph ph-trash"></i>
                    </button>
                </div>
            </td>
        </tr>`;
    }).join('');
}

function cpStatusBadge(status) {
    switch (status) {
        case 'PAGA':    return `<span class="status-badge paga"><i class="ph ph-check-circle"></i> Paga</span>`;
        case 'VENCIDA': return `<span class="status-badge vencida"><i class="ph ph-warning-circle"></i> Vencida</span>`;
        default:        return `<span class="status-badge aberto"><i class="ph ph-clock"></i> Em Aberto</span>`;
    }
}

function cpFormatarVencimento(conta) {
    if (!conta.dataVencimento) return '—';
    const [y, m, d] = conta.dataVencimento.split('-');
    return `${d}/${m}/${y}`;
}

function cpFiltrarConsulta() {
    const termo = document.getElementById('cpFiltroBusca').value.trim();
    cpCarregarContas(termo);
}

/* ── Wizard: navegação entre etapas ────────────────────────────────── */
function cpAvancarEtapa(etapa) {
    if (etapa === 2) {
        const desc = document.getElementById('cpDescricao').value.trim();
        if (!desc) { mostrarToast('Informe a descrição/nome da conta.', 'warning'); return; }
    }

    [1, 2, 3].forEach(n => {
        const panel = document.getElementById(`cpStep${n}`);
        if (panel) panel.style.display = 'none';
    });

    document.querySelectorAll('.wizard-step').forEach(el => {
        const s = parseInt(el.dataset.step);
        el.classList.remove('active', 'completed');
        if (s === etapa) el.classList.add('active');
        if (s < etapa)  el.classList.add('completed');
    });

    const panel = document.getElementById(`cpStep${etapa}`);
    if (panel) panel.style.display = 'block';
    cpEtapaAtual = etapa;

    if (etapa === 3 && cpItensAdicionados.length > 0) {
        const totalItens = cpItensAdicionados.reduce((acc, i) => acc + i.quantidade * i.valorUnitario, 0);
        const inputTotal = document.getElementById('cpValorTotalParcelas');
        if (!inputTotal.value) {
            inputTotal.value = formatarMoedaRaw(totalItens);
            cpDistribuirValor();
        }
    }
}

/* ── Wizard: Etapa 2 — Busca de Produto ────────────────────────────── */
async function cpBuscarProduto() {
    const termo = document.getElementById('cpBuscaProduto').value.trim();
    if (!termo) { mostrarToast('Digite um termo para buscar.', 'warning'); return; }
    try {
        const res = await fetch(`${API_URL}/api/produtos?nome=${encodeURIComponent(termo)}`, { headers: getAuthHeader() });
        const produtos = await res.json();
        cpRenderizarResultadosProduto(produtos);
    } catch(e) {
        mostrarToast('Erro ao buscar produtos.', 'error');
    }
}

function cpRenderizarResultadosProduto(produtos) {
    const container = document.getElementById('cpResultadosProduto');
    if (!produtos || produtos.length === 0) {
        container.style.display = 'block';
        container.innerHTML = `<div style="padding:12px;text-align:center;color:var(--text-muted);font-size:12px;">Nenhum produto encontrado.</div>`;
        return;
    }
    container.style.display = 'block';
    container.innerHTML = produtos.map(p => {
        const nome = (p.nome || '').replace(/'/g, "\\'");
        const custo = p.valorCusto || p.valor || 0;
        return `
        <div class="cp-produto-item" onclick="cpAdicionarProdutoModal(${p.codProduto}, '${nome}', ${custo})">
            <div>
                <div class="cp-produto-item-nome">${p.nome}</div>
                <div class="cp-produto-item-info">${p.tipo || ''} · Estoque: ${p.quantidadeEstoque ?? '—'}</div>
            </div>
            <div style="display:flex;gap:8px;align-items:center;">
                <span style="font-size:12px;color:var(--text-muted);">${formatarMoeda(p.valor)}</span>
                <button class="btn-action cp-produto-item-btn" onclick="event.stopPropagation();cpAdicionarProdutoModal(${p.codProduto}, '${nome}', ${custo})">
                    <i class="ph ph-plus"></i> Add
                </button>
            </div>
        </div>`;
    }).join('');
}

function cpAdicionarProdutoModal(codProduto, nome, valorUnitarioPadrao) {
    const existe = cpItensAdicionados.find(i => i.codProduto === codProduto);
    if (existe) { existe.quantidade++; }
    else { cpItensAdicionados.push({ codProduto, nome, quantidade: 1, valorUnitario: Number(valorUnitarioPadrao) || 0 }); }
    cpRenderizarItens();
    document.getElementById('cpResultadosProduto').style.display = 'none';
    document.getElementById('cpBuscaProduto').value = '';
}

function cpRenderizarItens() {
    const tbody = document.getElementById('cpItensCorpo');
    if (cpItensAdicionados.length === 0) {
        tbody.innerHTML = `<tr id="cpItensVazio"><td colspan="5" style="text-align:center;color:var(--text-muted);padding:16px;font-size:12px;">
            Nenhum produto adicionado (opcional)</td></tr>`;
        document.getElementById('cpTotalItensLabel').innerText = 'Total: R$ 0,00';
        return;
    }
    let totalItens = 0;
    tbody.innerHTML = cpItensAdicionados.map((item, idx) => {
        const subtotal = Number((item.quantidade * item.valorUnitario).toFixed(2));
        totalItens += subtotal;
        return `
        <tr>
            <td style="font-size:13px;">${item.nome}</td>
            <td class="align-center">
                <input type="number" min="1" value="${item.quantidade}" style="width:60px;text-align:center;padding:4px 6px;border:1px solid var(--border-subtle);border-radius:4px;font-size:12px;"
                    onchange="cpAtualizarQuantidade(${idx}, this.value)">
            </td>
            <td class="align-right">
                <input type="text" value="${formatarMoedaRaw(item.valorUnitario)}" style="width:90px;text-align:right;padding:4px 6px;border:1px solid var(--border-subtle);border-radius:4px;font-size:12px;"
                    oninput="mascaraMoeda(this)" onblur="cpAtualizarValorUnitario(${idx}, this.value)">
            </td>
            <td class="align-right" style="font-size:12px;font-weight:600;">${formatarMoeda(subtotal)}</td>
            <td class="align-center">
                <button class="btn-action btn-danger" onclick="cpRemoverItem(${idx})" style="padding:3px 8px;">
                    <i class="ph ph-trash"></i>
                </button>
            </td>
        </tr>`;
    }).join('');
    document.getElementById('cpTotalItensLabel').innerText = `Total: ${formatarMoeda(totalItens)}`;
}

function cpAtualizarQuantidade(idx, valor) {
    cpItensAdicionados[idx].quantidade = Math.max(1, parseInt(valor) || 1);
    cpRenderizarItens();
}

function cpAtualizarValorUnitario(idx, valorStr) {
    cpItensAdicionados[idx].valorUnitario = converterMoedaParaFloat(valorStr);
    cpRenderizarItens();
}

function cpRemoverItem(idx) {
    cpItensAdicionados.splice(idx, 1);
    cpRenderizarItens();
}

/* ── Wizard: Etapa 3 — Parcelas ─────────────────────────────────────── */
function cpTogglePaga() { cpGerarParcelas(); }

function cpDistribuirValor() {
    const n = parseInt(document.getElementById('cpNumeroParcelas').value) || 1;
    const total = converterMoedaParaFloat(document.getElementById('cpValorTotalParcelas').value);
    if (!total || n < 1) return;
    const valorParcela = Math.floor((total / n) * 100) / 100;
    const diff = Number((total - valorParcela * n).toFixed(2));
    const inputs = document.querySelectorAll('.cp-parcela-valor');
    inputs.forEach((inp, i) => {
        const v = i === inputs.length - 1 ? valorParcela + diff : valorParcela;
        inp.value = formatarMoedaRaw(v);
    });
}

function cpGerarParcelas() {
    const n = parseInt(document.getElementById('cpNumeroParcelas').value) || 1;
    const total = converterMoedaParaFloat(document.getElementById('cpValorTotalParcelas').value);
    const primeiraDataVal = document.getElementById('cpPrimeiroVencimento').value;
    const jaPaga = document.getElementById('cpJaPaga').checked;
    const valorParcela = total > 0 ? Math.floor((total / n) * 100) / 100 : 0;
    const diff = total > 0 ? Number((total - valorParcela * n).toFixed(2)) : 0;

    const tbody = document.getElementById('cpParcelasCorpo');
    tbody.innerHTML = '';
    for (let i = 1; i <= n; i++) {
        const v = i === n ? valorParcela + diff : valorParcela;
        let dataStr = '';
        if (primeiraDataVal) {
            const dt = new Date(primeiraDataVal + 'T00:00:00');
            dt.setMonth(dt.getMonth() + (i - 1));
            dataStr = dt.toISOString().split('T')[0];
        }
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="align-center" style="font-size:12px;font-weight:600;">${i}/${n}</td>
            <td class="align-right">
                <input type="text" class="cp-parcela-valor" value="${formatarMoedaRaw(v)}"
                    oninput="mascaraMoeda(this)" style="width:100px;text-align:right;">
            </td>
            <td class="align-center">
                <input type="date" class="cp-parcela-data" value="${dataStr}"
                    style="width:130px;" ${(!jaPaga) ? 'required' : ''}>
            </td>
            <td class="align-center">
                <input type="checkbox" class="cp-parcela-paga-check" ${jaPaga ? 'checked' : ''}>
            </td>`;
        tbody.appendChild(tr);
    }
}

/* ── Salvar conta ───────────────────────────────────────────────────── */
async function cpSalvarConta() {
    const descricao  = document.getElementById('cpDescricao').value.trim();
    const fornecedor = document.getElementById('cpFornecedor').value.trim();
    const nf         = document.getElementById('cpNotaFiscal').value.trim();
    const jaPaga     = document.getElementById('cpJaPaga').checked;
    const contaId    = document.getElementById('cpContaId').value;

    if (!descricao) { mostrarToast('Informe a descrição da conta.', 'warning'); return; }

    const parcelasRows = document.querySelectorAll('#cpParcelasCorpo tr');
    if (parcelasRows.length === 0 || (parcelasRows.length === 1 && parcelasRows[0].querySelector('.cp-parcela-valor') === null)) {
        mostrarToast('Gere ao menos uma parcela antes de salvar.', 'warning'); return;
    }

    const parcelas = [];
    let ok = true;
    parcelasRows.forEach((tr, i) => {
        const valorStr = tr.querySelector('.cp-parcela-valor')?.value || '';
        const dataVal  = tr.querySelector('.cp-parcela-data')?.value || '';
        const paga     = tr.querySelector('.cp-parcela-paga-check')?.checked || false;
        const valor    = converterMoedaParaFloat(valorStr);
        if (valor <= 0) { mostrarToast(`Parcela ${i+1}: valor inválido.`, 'warning'); ok = false; return; }
        if (!jaPaga && !dataVal) { mostrarToast(`Parcela ${i+1}: informe a data de vencimento.`, 'warning'); ok = false; return; }
        parcelas.push({ numeroParcela: i + 1, valorParcela: valor, dataVencimento: dataVal || null, paga });
    });
    if (!ok) return;

    const itens = cpItensAdicionados.map(item => ({
        codProduto: item.codProduto,
        quantidade: item.quantidade,
        valorUnitario: Number(item.valorUnitario.toFixed(2))
    }));

    const payload = { descricao, fornecedor: fornecedor || null, numeroNotaFiscal: nf || null, paga: jaPaga, itens, parcelas };
    const btn = document.getElementById('btnCpSalvar');
    const txtOrig = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Salvando...';

    try {
        const isEdit = !!contaId;
        const url    = isEdit ? `${API_URL}/api/contas-pagar/${contaId}` : `${API_URL}/api/contas-pagar`;
        const res = await fetch(url, {
            method: isEdit ? 'PUT' : 'POST',
            headers: { ...getAuthHeader(), 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const body = await res.json().catch(() => ({}));
        if (res.ok) {
            mostrarToast(isEdit ? 'Conta atualizada!' : 'Conta cadastrada com sucesso!', 'success');
            cpNavegar('consulta');
            cpCarregarContas();
        } else {
            mostrarToast(body.erro || 'Erro ao salvar conta.', 'error');
        }
    } catch(e) {
        mostrarToast('Erro de conexão ao salvar.', 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = txtOrig;
    }
}

/* ── Pagar / Editar / Deletar ───────────────────────────────────────── */
async function cpMarcarComoPaga(id) {
    if (!confirm('Marcar esta conta como PAGA? Isso irá quitar todas as parcelas.')) return;
    try {
        const res = await fetch(`${API_URL}/api/contas-pagar/${id}/pagar`, { method: 'PATCH', headers: getAuthHeader() });
        if (res.ok) { mostrarToast('Conta quitada com sucesso!', 'success'); cpCarregarContas(); }
        else mostrarToast('Erro ao quitar conta.', 'error');
    } catch(e) { mostrarToast('Erro de conexão.', 'error'); }
}

async function cpEditarConta(id) {
    try {
        const res = await fetch(`${API_URL}/api/contas-pagar/${id}`, { headers: getAuthHeader() });
        if (!res.ok) { mostrarToast('Conta não encontrada.', 'error'); return; }
        const conta = await res.json();

        cpNavegar('form');
        document.getElementById('tituloJanelaContasPagar').innerText = 'Contas a Pagar › Editar';
        document.getElementById('cpContaId').value    = conta.codContaPagar;
        document.getElementById('cpDescricao').value  = conta.descricao || '';
        document.getElementById('cpFornecedor').value = conta.fornecedor || '';
        document.getElementById('cpNotaFiscal').value = conta.numeroNotaFiscal || '';

        cpItensAdicionados = (conta.itens || []).map(item => ({
            codProduto: item.produto?.codProduto,
            nome: item.produto?.nome || '—',
            quantidade: item.quantidade,
            valorUnitario: Number(item.valorUnitario) || 0
        }));
        cpRenderizarItens();

        document.getElementById('cpJaPaga').checked = conta.paga || false;
        const parcelas = conta.parcelas || [];
        document.getElementById('cpNumeroParcelas').value = parcelas.length || 1;

        cpAvancarEtapa(3);

        const tbody = document.getElementById('cpParcelasCorpo');
        tbody.innerHTML = '';
        parcelas.forEach((p, i) => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td class="align-center" style="font-size:12px;font-weight:600;">${i+1}/${parcelas.length}</td>
                <td class="align-right">
                    <input type="text" class="cp-parcela-valor" value="${formatarMoedaRaw(p.valorParcela)}"
                        oninput="mascaraMoeda(this)" style="width:100px;text-align:right;">
                </td>
                <td class="align-center">
                    <input type="date" class="cp-parcela-data" value="${p.dataVencimento || ''}" style="width:130px;">
                </td>
                <td class="align-center">
                    <input type="checkbox" class="cp-parcela-paga-check" ${p.paga ? 'checked' : ''}>
                </td>`;
            tbody.appendChild(tr);
        });
    } catch(e) { mostrarToast('Erro ao carregar conta para edição.', 'error'); }
}

function cpConfirmarDeletar(id, descricao) {
    if (!confirm(`Excluir a conta "${descricao}"?\n\nO estoque dos produtos vinculados será revertido.`)) return;
    cpDeletar(id);
}

async function cpDeletar(id) {
    try {
        const res = await fetch(`${API_URL}/api/contas-pagar/${id}`, { method: 'DELETE', headers: getAuthHeader() });
        if (res.ok || res.status === 204) { mostrarToast('Conta excluída com sucesso.', 'success'); cpCarregarContas(); }
        else mostrarToast('Erro ao excluir conta.', 'error');
    } catch(e) { mostrarToast('Erro de conexão ao excluir.', 'error'); }
}

/* ── Reset do formulário ────────────────────────────────────────────── */
function cpResetarFormulario() {
    ['cpContaId','cpDescricao','cpFornecedor','cpNotaFiscal'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    document.getElementById('cpJaPaga').checked = false;
    document.getElementById('cpNumeroParcelas').value = '1';
    document.getElementById('cpValorTotalParcelas').value = '';
    document.getElementById('cpPrimeiroVencimento').value = '';
    document.getElementById('cpBuscaProduto').value = '';
    document.getElementById('cpResultadosProduto').style.display = 'none';
    document.getElementById('cpParcelasCorpo').innerHTML = `<tr><td colspan="4" style="text-align:center;color:var(--text-muted);padding:16px;font-size:12px;">
        Configure e clique em "Gerar Parcelas"</td></tr>`;
    cpItensAdicionados = [];
    cpRenderizarItens();
    [1, 2, 3].forEach(n => {
        const p = document.getElementById(`cpStep${n}`);
        if (p) p.style.display = n === 1 ? 'block' : 'none';
    });
    document.querySelectorAll('.wizard-step').forEach(el => {
        el.classList.remove('active', 'completed');
        if (parseInt(el.dataset.step) === 1) el.classList.add('active');
    });
    cpEtapaAtual = 1;
}

/* ==========================================================================
   UTILITÁRIO GLOBAL: TOAST NOTIFICATIONS
   ========================================================================== */
function mostrarToast(mensagem, tipo = 'success') {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    const icones = { success: 'ph-check-circle', error: 'ph-x-circle', warning: 'ph-warning' };
    const toast = document.createElement('div');
    toast.className = `toast ${tipo}`;
    toast.innerHTML = `<i class="ph ${icones[tipo] || 'ph-info'}"></i> ${mensagem}`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.animation = 'toastOut 0.3s ease forwards';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

/* Auxiliar: formata número → "R$ X,XX" sem o campo de input */
function formatarMoedaRaw(valor) {
    const v = Number(valor) || 0;
    return 'R$ ' + v.toFixed(2).replace('.', ',').replace(/(\d)(?=(\d{3})+,)/g, '$1.');
}

/* ==========================================================================
   MÓDULO 10: PRODUÇÃO
   ========================================================================== */

/* ── Estado ─────────────────────────────────────────────────────────── */
let prodProdutoSelecionado = null; // objeto produto vindo da API
let prodCalculoAtual       = null; // última resposta do endpoint /calcular

/* ── Bootstrap ──────────────────────────────────────────────────────── */
function setupProducaoModule() {
    document.getElementById('btnNovaProd')?.addEventListener('click', () => prodNavegar('form'));
    document.getElementById('btnHistoricoProd')?.addEventListener('click', () => {
        prodNavegar('historico');
        prodCarregarHistorico();
    });
    document.getElementById('btnNovaProdHistorico')?.addEventListener('click', () => prodNavegar('form'));
    document.getElementById('prodBuscaProduto')?.addEventListener('keyup', e => {
        if (e.key === 'Enter') prodBuscarProduto();
    });
    document.getElementById('prodQuantidade')?.addEventListener('input', () => {
        // limpa preview ao mudar quantidade manualmente
        prodLimparPreview();
    });
}

/* ── Navegação (views de produção vivem dentro da janela de Produtos) ── */
function prodNavegar(tela) {
    if (tela === 'historico') navegarProdutos('prodHistorico');
    else if (tela === 'form') navegarProdutos('prodForm');
    else navegarProdutos('menu');
}

/* ── Histórico ──────────────────────────────────────────────────────── */
async function prodCarregarHistorico() {
    const tbody = document.getElementById('prodHistoricoCorpo');
    tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:24px;">
        <i class="ph ph-spinner ph-spin"></i> Carregando...</td></tr>`;
    try {
        const res = await fetch(`${API_URL}/api/producao`, { headers: getAuthHeader() });
        if (!res.ok) throw new Error('Erro ao buscar histórico.');
        const lista = await res.json();
        prodRenderizarHistorico(lista);
    } catch(e) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--danger);padding:24px;">${e.message}</td></tr>`;
    }
}

function prodRenderizarHistorico(lista) {
    const tbody = document.getElementById('prodHistoricoCorpo');
    if (!lista || lista.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:32px;">
            Nenhuma produção registrada.</td></tr>`;
        return;
    }
    tbody.innerHTML = lista.map(p => {
        const estornada = p.estornada === true;
        const badge     = estornada
            ? `<span class="status-badge estornada"><i class="ph ph-arrow-u-up-left"></i> Estornada</span>`
            : `<span class="status-badge realizada"><i class="ph ph-check-circle"></i> Realizada</span>`;
        const data = p.dataProducao
            ? new Date(p.dataProducao).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
            : '—';
        const nomeProd = p.produto?.nome || '—';
        const btnEstornar = !estornada
            ? `<button class="btn-action btn-danger" onclick="prodConfirmarEstorno(${p.codProducao}, '${nomeProd.replace(/'/g,"\\'")}')">
                   <i class="ph ph-arrow-u-up-left"></i> Estornar
               </button>` : '';
        return `
        <tr>
            <td>
                <div style="font-weight:600;font-size:13px;">${nomeProd}</div>
                <div style="font-size:11px;color:var(--text-muted);">Tipo: ${p.produto?.tipo || '—'}</div>
            </td>
            <td class="align-center" style="font-weight:700;">${p.quantidadeProduzida ?? '—'}</td>
            <td class="align-center" style="font-size:12px;">${data}</td>
            <td class="align-center">${badge}</td>
            <td class="align-center">${btnEstornar}</td>
        </tr>`;
    }).join('');
}

/* ── Formulário: Busca de Produto ────────────────────────────────────── */
async function prodBuscarProduto() {
    const termo = document.getElementById('prodBuscaProduto').value.trim();
    if (!termo) { mostrarToast('Digite o nome do produto para buscar.', 'warning'); return; }

    const container = document.getElementById('prodResultadosBusca');
    container.style.display = 'block';
    container.innerHTML = `<div style="padding:12px;text-align:center;color:var(--text-muted);font-size:12px;">
        <i class="ph ph-spinner ph-spin"></i> Buscando...</div>`;
    try {
        const res = await fetch(`${API_URL}/api/produtos?nome=${encodeURIComponent(termo)}`, { headers: getAuthHeader() });
        const todos = await res.json();
        // Filtra apenas PF e MPPF
        const produtivos = todos.filter(p => p.tipo === 'PF' || p.tipo === 'MPPF');
        prodRenderizarResultadosBusca(produtivos);
    } catch(e) {
        container.innerHTML = `<div style="padding:12px;text-align:center;color:var(--danger);font-size:12px;">${e.message}</div>`;
    }
}

function prodRenderizarResultadosBusca(produtos) {
    const container = document.getElementById('prodResultadosBusca');
    if (!produtos || produtos.length === 0) {
        container.innerHTML = `<div style="padding:12px;text-align:center;color:var(--text-muted);font-size:12px;">
            Nenhum produto PF ou MPPF encontrado.</div>`;
        return;
    }
    container.innerHTML = produtos.map(p => {
        const nome = (p.nome || '').replace(/'/g, "\\'");
        const estoqueStr = `Estoque atual: ${p.quantidadeEstoque ?? 0} un.`;
        return `
        <div class="cp-produto-item" onclick="prodSelecionarProduto(${p.codProduto})">
            <div>
                <div class="cp-produto-item-nome">${p.nome}</div>
                <div class="cp-produto-item-info">${p.tipo} · ${estoqueStr}</div>
            </div>
            <button class="btn-action" style="font-size:11px;" onclick="event.stopPropagation();prodSelecionarProduto(${p.codProduto})">
                <i class="ph ph-check"></i> Selecionar
            </button>
        </div>`;
    }).join('');
}

async function prodSelecionarProduto(codProduto) {
    try {
        const res = await fetch(`${API_URL}/api/produtos/${codProduto}`, { headers: getAuthHeader() });
        if (!res.ok) throw new Error('Produto não encontrado.');
        const produto = await res.json();

        prodProdutoSelecionado = produto;

        // Mostra card do produto
        document.getElementById('prodNomeProduto').innerText   = produto.nome;
        document.getElementById('prodTipoProduto').innerHTML   = `<i class="ph ph-tag"></i> ${produto.tipo}`;
        document.getElementById('prodEstoqueProduto').innerText = `Estoque atual: ${produto.quantidadeEstoque ?? 0} unidades`;
        document.getElementById('prodProdutoSelecionado').style.display = 'flex';

        // Esconde resultados e campo de busca
        document.getElementById('prodResultadosBusca').style.display = 'none';
        document.getElementById('prodBuscaProduto').value = '';

        // Mostra passo 2
        document.getElementById('prodStep2').style.display = 'block';

        prodLimparPreview();
    } catch(e) {
        mostrarToast('Erro ao carregar produto.', 'error');
    }
}

function prodLimparSelecao() {
    prodProdutoSelecionado = null;
    prodCalculoAtual       = null;
    document.getElementById('prodProdutoSelecionado').style.display = 'none';
    document.getElementById('prodStep2').style.display = 'none';
    document.getElementById('prodResultadosBusca').style.display = 'none';
    document.getElementById('prodBuscaProduto').value = '';
    prodLimparPreview();
}

function prodLimparPreview() {
    prodCalculoAtual = null;
    document.getElementById('prodStep3').style.display    = 'none';
    document.getElementById('prodAcoes').style.display    = 'none';
    document.getElementById('prodPreviewContainer').innerHTML = '';
    document.getElementById('prodAlertaContainer').innerHTML  = '';
}

/* ── Calcular insumos necessários ────────────────────────────────────── */
async function prodCalcularPreview() {
    if (!prodProdutoSelecionado) { mostrarToast('Selecione um produto primeiro.', 'warning'); return; }

    const qtd = parseInt(document.getElementById('prodQuantidade').value) || 0;
    if (qtd <= 0) { mostrarToast('Informe uma quantidade válida.', 'warning'); return; }

    const btnCalc = document.querySelector('#prodStep2 button');
    const txtOrig = btnCalc.innerHTML;
    btnCalc.disabled = true;
    btnCalc.innerHTML = '<i class="ph ph-spinner ph-spin"></i>';

    try {
        const res = await fetch(`${API_URL}/api/producao/calcular`, {
            method: 'POST',
            headers: { ...getAuthHeader(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ codProduto: prodProdutoSelecionado.codProduto, quantidade: qtd })
        });
        const body = await res.json();
        if (!res.ok) { mostrarToast(body.erro || 'Erro ao calcular.', 'error'); return; }

        prodCalculoAtual = body;
        prodRenderizarPreview(body);
    } catch(e) {
        mostrarToast('Erro de conexão ao calcular.', 'error');
    } finally {
        btnCalc.disabled = false;
        btnCalc.innerHTML = txtOrig;
    }
}

function prodRenderizarPreview(calculo) {
    const containerPreview = document.getElementById('prodPreviewContainer');
    const containerAlerta  = document.getElementById('prodAlertaContainer');

    // Tabela de insumos
    const linhas = (calculo.insumos || []).map(ins => {
        const badge = ins.suficiente
            ? `<span class="prod-badge-ok"><i class="ph ph-check"></i> OK</span>`
            : `<span class="prod-badge-nok"><i class="ph ph-warning"></i> Insuf.</span>`;
        const necessaria = Number(ins.quantidadeNecessaria).toFixed(4).replace(/\.?0+$/, '');
        return `
        <div class="prod-preview-row">
            <div style="font-weight:500;">${ins.nomeInsumo}</div>
            <div class="align-right" style="font-size:12px;">${necessaria}</div>
            <div class="align-right" style="font-size:12px;">${ins.quantidadeDisponivel}</div>
            <div class="align-center">${badge}</div>
        </div>`;
    }).join('');

    containerPreview.innerHTML = `
        <div class="prod-preview-header">
            <span>Insumo (MP)</span>
            <span style="text-align:right;">Necessário</span>
            <span style="text-align:right;">Disponível</span>
            <span style="text-align:center;">Status</span>
        </div>
        ${linhas}`;

    // Alerta de viabilidade
    if (calculo.podeRealizar) {
        containerAlerta.innerHTML = `
            <div class="prod-alerta ok">
                <i class="ph ph-check-circle"></i>
                <div><strong>Produção viável!</strong> Todos os insumos têm estoque suficiente. Clique em "Confirmar Produção" para executar.</div>
            </div>`;
    } else {
        const faltantes = (calculo.insumos || [])
            .filter(i => !i.suficiente)
            .map(i => `<strong>${i.nomeInsumo}</strong>`)
            .join(', ');
        containerAlerta.innerHTML = `
            <div class="prod-alerta erro">
                <i class="ph ph-warning-circle"></i>
                <div><strong>Estoque insuficiente!</strong> Os seguintes insumos não têm quantidade suficiente: ${faltantes}.</div>
            </div>`;
    }

    // Mostra passo 3 e ações
    document.getElementById('prodStep3').style.display = 'block';
    document.getElementById('prodAcoes').style.display = 'block';

    const btnConfirmar = document.getElementById('btnConfirmarProducao');
    btnConfirmar.disabled = !calculo.podeRealizar;
    btnConfirmar.style.opacity = calculo.podeRealizar ? '1' : '0.5';

    document.getElementById('prodResumoFinal').innerHTML =
        `Produzir <strong>${calculo.quantidadeSolicitada}</strong> unidade(s) de <strong>${calculo.nomeProduto}</strong>`;
}

/* ── Confirmar produção ──────────────────────────────────────────────── */
async function prodConfirmar() {
    if (!prodCalculoAtual || !prodCalculoAtual.podeRealizar) {
        mostrarToast('Verifique os insumos antes de confirmar.', 'warning'); return;
    }
    const qtd = parseInt(document.getElementById('prodQuantidade').value) || 0;
    if (!confirm(`Confirmar a produção de ${qtd} unidade(s) de "${prodCalculoAtual.nomeProduto}"?\n\nOs insumos serão debitados do estoque.`)) return;

    const btn = document.getElementById('btnConfirmarProducao');
    const txtOrig = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<i class="ph ph-spinner ph-spin"></i> Produzindo...';

    try {
        const res = await fetch(`${API_URL}/api/producao`, {
            method: 'POST',
            headers: { ...getAuthHeader(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ codProduto: prodProdutoSelecionado.codProduto, quantidade: qtd })
        });
        const body = await res.json();
        if (res.ok) {
            mostrarToast(`${qtd} unidade(s) de "${prodCalculoAtual.nomeProduto}" produzidas com sucesso!`, 'success');
            prodNavegar('historico');
            prodCarregarHistorico();
        } else {
            mostrarToast(body.erro || 'Erro ao realizar produção.', 'error');
        }
    } catch(e) {
        mostrarToast('Erro de conexão ao produzir.', 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = txtOrig;
    }
}

/* ── Estornar produção ───────────────────────────────────────────────── */
function prodConfirmarEstorno(id, nomeProduto) {
    if (!confirm(`Estornar a produção de "${nomeProduto}"?\n\nOs insumos serão devolvidos ao estoque e o produto final será removido.`)) return;
    prodEstornar(id);
}

async function prodEstornar(id) {
    try {
        const res = await fetch(`${API_URL}/api/producao/${id}/estornar`, {
            method: 'PATCH', headers: getAuthHeader()
        });
        const body = await res.json();
        if (res.ok) {
            mostrarToast('Produção estornada com sucesso.', 'success');
            prodCarregarHistorico();
        } else {
            mostrarToast(body.erro || 'Erro ao estornar.', 'error');
        }
    } catch(e) { mostrarToast('Erro de conexão ao estornar.', 'error'); }
}

/* ── Reset ───────────────────────────────────────────────────────────── */
function prodResetarFormulario() {
    prodProdutoSelecionado = null;
    prodCalculoAtual       = null;
    const el = id => document.getElementById(id);
    el('prodBuscaProduto').value = '';
    el('prodResultadosBusca').style.display    = 'none';
    el('prodProdutoSelecionado').style.display = 'none';
    el('prodStep2').style.display              = 'none';
    el('prodStep3').style.display              = 'none';
    el('prodAcoes').style.display              = 'none';
    el('prodPreviewContainer').innerHTML       = '';
    el('prodAlertaContainer').innerHTML        = '';
    el('prodQuantidade').value                 = '1';
    el('prodNomeProduto').innerText            = '';
    el('prodTipoProduto').innerHTML            = '';
    el('prodEstoqueProduto').innerText         = '';
}