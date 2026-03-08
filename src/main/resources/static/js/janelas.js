/* =================================================================================
   1. VARIÁVEIS GERAIS E CONFIGURAÇÃO
   ================================================================================= */
// Ajuste a URL conforme seu backend. Se não tiver "/api" no application.properties, remova.
const API_URL = "https://horus-api-cjb4.onrender.com"; 
//const API_URL = "http://localhost:8080";
let contadorCascata = 0; 
const deslocamentoPx = 30;

/* =================================================================================
   2. INICIALIZAÇÃO (LOAD) E EVENTOS GLOBAIS
   ================================================================================= */
window.addEventListener('load', () => {
    // Inicializa listeners dos botões da barra lateral
    setupNavbarListeners();
    
    // Inicializa listeners específicos de Produtos (Cadastro e Consulta)
    setupProductEvents();

    // Inicializa listeners específicos de Relatórios
    setupReportEvents();
    
    // Configura todas as janelas arrastáveis (Padrão do sistema)
    document.querySelectorAll('.draggable').forEach(win => {
        makeDraggable(win);
        win.classList.remove('is-visible'); 
        win.addEventListener('mousedown', () => bringToFront(win));
    });

    // Configura botões de fechar (X)
    document.querySelectorAll('.close-btn').forEach(button => {
        button.addEventListener('click', function() {
            const windowDiv = this.closest('.draggable');
            fecharJanela(windowDiv);
        });
    });

    const relatorios = document.getElementById('relatoriosDiv');
    const caixa = document.getElementById('caixaDiv');
    const body = document.body;

    if (relatorios && relatorios.parentElement !== body) {
        console.warn("Detectado erro de aninhamento! Movendo Relatórios para o Body...");
        body.appendChild(relatorios); // Move a div para o final do body, salvando a estrutura
    }
});

/* =================================================================================
   3. LÓGICA DE UI (JANELAS, ARRASTAR E POSICIONAR)
   ================================================================================= */

/**
 * Configura os botões da barra lateral esquerda
 */
/**
 * Configura os botões da barra lateral esquerda
 */
function setupNavbarListeners() {
    const buttonMap = {
        'produtosBtn': 'produtosDiv',      
        'relatoriosBtn': 'relatoriosDiv',
        'caixaBtn': 'caixaDiv'
    };

    for (const [btnId, windowId] of Object.entries(buttonMap)) {
        const btn = document.getElementById(btnId);
        const janela = document.getElementById(windowId);

        if (btn && janela) {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                console.log("Abrindo janela:", windowId); // Debug
                
                // Reseta a navegação interna para o menu principal da janela
                if (windowId === 'produtosDiv' && typeof navegarProdutos === 'function') {
                    navegarProdutos('menu');
                }
                if (windowId === 'relatoriosDiv' && typeof navegarRelatorios === 'function') {
                    navegarRelatorios('menu');
                }
                // (Opcional) Se quiser resetar o caixa também, descomente:
                // if (windowId === 'caixaDiv') navegarCaixa('menu'); 

                abrirJanela(janela);
            });
        }
    }
}

/* =================================================================================
   FUNÇÕES DE JANELA (ABRIR, FECHAR E POSICIONAR)
   ================================================================================= */

/**
 * Abre uma janela, traz para frente e posiciona em cascata
 */
function abrirJanela(janela) {
    // 1. Torna visível
    janela.style.display = 'block';
    janela.classList.add('is-visible');

    // 2. Traz para frente (Z-Index máximo)
    bringToFront(janela);

    // 3. Aplica o posicionamento em Cascata
    posicionarEmCascata(janela);
}

/**
 * Calcula a posição em Cascata (Centro + Deslocamento)
 */
function posicionarEmCascata(el) {
    const winW = window.innerWidth;
    const winH = window.innerHeight;

    // Pega as dimensões da janela (usa 600x400 como fallback se falhar)
    const width = el.offsetWidth || 600;
    const height = el.offsetHeight || 400;

    // 1. Calcula o centro exato da tela
    let posX = (winW / 2) - (width / 2);
    let posY = (winH / 2) - (height / 2);

    // 2. Adiciona o deslocamento baseado no número de janelas abertas
    posX += (contadorCascata * deslocamentoPx);
    posY += (contadorCascata * deslocamentoPx);

    // 3. Verificação de Segurança: Vai sair da tela?
    // Se a janela for abrir muito pra fora, reseta o contador e volta pro centro
    if (posX + width > winW || posY + height > winH) {
        contadorCascata = 0;
        posX = (winW / 2) - (width / 2);
        posY = (winH / 2) - (height / 2);
    }

    // 4. Aplica as posições finais (Math.max evita que fique negativo e suma da tela)
    el.style.left = Math.max(0, posX) + 'px';
    el.style.top = Math.max(0, posY) + 'px';

    // 5. Incrementa o contador para a próxima janela que for aberta
    contadorCascata++;
}

/**
 * Fecha a janela e gerencia o reset da cascata
 */
function fecharJanela(windowDiv) {
    if (windowDiv) {
        // Esconde a janela
        windowDiv.classList.remove('is-visible');
        windowDiv.style.display = 'none';
        
        // Verifica se ainda tem alguma janela aberta na tela
        const abertas = document.querySelectorAll('.draggable.is-visible');
        
        // Se fechou todas as janelas, reseta a cascata para a próxima abrir no centro
        if (abertas.length === 0) {
            contadorCascata = 0;
        }

        // Reseta navegação interna para voltar ao menu ao reabrir
        if(windowDiv.id === 'produtosDiv' && typeof navegarProdutos === 'function') navegarProdutos('menu');
        if(windowDiv.id === 'relatoriosDiv' && typeof navegarRelatorios === 'function') navegarRelatorios('menu');
    }
}

/**
 * Função Auxiliar para Centralizar Forçadamente
 */
function centralizarJanelaForcada(el) {
    const winW = window.innerWidth;
    const winH = window.innerHeight;
    
    // Pega as dimensões do elemento (precisa estar display:block para medir)
    const width = el.offsetWidth || 600; // Valor padrão caso falhe
    const height = el.offsetHeight || 400;

    const posX = (winW / 2) - (width / 2);
    const posY = (winH / 2) - (height / 2);

    el.style.left = Math.max(0, posX) + 'px'; // Evita negativo
    el.style.top = Math.max(0, posY) + 'px';
}
/**
 * Fecha uma janela e reseta o contador se necessário
 */
function fecharJanela(windowDiv) {
    if (windowDiv) {
        windowDiv.classList.remove('is-visible');
        
        // Reset da cascata se fechar tudo
        const abertas = document.querySelectorAll('.draggable.is-visible');
        if (abertas.length === 0) contadorCascata = 0;

        // Reseta navegação interna (Opcional: Voltar ao menu principal da janela)
        if(windowDiv.id === 'produtosDiv') navegarProdutos('menu');
        if(windowDiv.id === 'relatoriosDiv') navegarRelatorios('menu');
        if(windowDiv.id === 'caixaDiv') navegarRelatorios('menu');
    }
}

/**
 * Centraliza e aplica efeito cascata nas janelas
 */
function posicionarJanela(el) {
    el.style.visibility = 'hidden';
    el.style.display = 'block';

    const rect = el.getBoundingClientRect();
    const winW = window.innerWidth;
    const winH = window.innerHeight;

    let posX = (winW / 2) - (rect.width / 2);
    let posY = (winH / 2) - (rect.height / 2);

    posX += (contadorCascata * deslocamentoPx);
    posY += (contadorCascata * deslocamentoPx);

    if (posX + rect.width > winW || posY + rect.height > winH) {
        contadorCascata = 0;
        posX = (winW / 2) - (rect.width / 2);
        posY = (winH / 2) - (rect.height / 2);
    }

    el.style.left = posX + 'px';
    el.style.top = posY + 'px';
    el.style.transform = 'none'; 
    el.style.visibility = '';
    el.style.display = ''; 
}

/**
 * Traz a janela clicada para frente (Z-Index)
 */
function bringToFront(element) {
    document.querySelectorAll('.draggable').forEach(el => el.style.zIndex = '100');
    element.style.zIndex = '1000';
}

/**
 * Torna um elemento arrastável pelo cabeçalho
 */
function makeDraggable(elmnt) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    const header = elmnt.querySelector('.headerjanela') || elmnt;
    
    header.onmousedown = dragMouseDown;

    function dragMouseDown(e) {
        e = e || window.event;
        e.preventDefault();
        pos3 = e.clientX;
        pos4 = e.clientY;
        document.onmouseup = closeDragElement;
        document.onmousemove = elementDrag;
    }

    function elementDrag(e) {
        e = e || window.event;
        e.preventDefault();
        pos1 = pos3 - e.clientX;
        pos2 = pos4 - e.clientY;
        pos3 = e.clientX;
        pos4 = e.clientY;
        elmnt.style.top = (elmnt.offsetTop - pos2) + "px";
        elmnt.style.left = (elmnt.offsetLeft - pos1) + "px";
    }

    function closeDragElement() {
        document.onmouseup = null;
        document.onmousemove = null;
    }
}

/**
 * Exibe alertas simples
 */
function showMessage(message, isError = false) {
    alert(isError ? "Erro: " + message : "Sucesso: " + message);
}