

// ============================================================
// 1. FORMATAÇÃO, MÁSCARAS E CONVERSÃO
// ============================================================

/**
 * MÁSCARA DE INPUT (Para Desconto e Acréscimo)
 * O usuário digita apenas números e a função formata como dinheiro.
 * Ex: Digita "200" -> vira "R$ 2,00"
 * Uso: oninput="mascaraMoeda(this)"
 */
function mascaraMoeda(input) {
    // 1. Remove tudo que não for dígito
    let v = input.value.replace(/\D/g, '');

    // 2. Se apagou tudo, limpa o campo
    if (v === '') {
        input.value = '';
        return;
    }

    // 3. Divide por 100 para criar os centavos e fixa 2 casas
    v = (parseFloat(v) / 100).toFixed(2) + '';

    // 4. Troca o ponto decimal por vírgula
    v = v.replace(".", ",");

    // 5. Adiciona separador de milhar (Ex: 1.000,00)
    // Regex: procura grupos de 3 dígitos que não estejam no fim
    v = v.replace(/(\d)(\d{3})(\d{3}),/g, "$1.$2.$3,");
    v = v.replace(/(\d)(\d{3}),/g, "$1.$2,");

    // 6. Adiciona o prefixo
    input.value = 'R$ ' + v;
}

/**
 * FORMATAÇÃO PARA EXIBIÇÃO (Labels e Tabelas)
 * Ex: Recebe float 10.5 -> Retorna "R$ 10,50"
 */
function formatarMoeda(valor) {
    if(valor === null || valor === undefined) return "R$ 0,00";
    return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

/**
 * CONVERSOR PARA CÁLCULO (String -> Float)
 * Pega "R$ 1.250,50" e transforma em 1250.50 para o JS somar.
 */
function converterMoedaParaFloat(valorString) {
    if(!valorString || valorString === '') return 0.0;
    
    // 1. Remove tudo que não é número ou vírgula (Tira R$, pontos de milhar, espaços)
    let limpo = valorString.replace(/[^\d,]/g, '');
    
    // 2. Troca a vírgula por ponto (padrão americano)
    limpo = limpo.replace(',', '.');
    
    return parseFloat(limpo) || 0.0;
}

// ============================================================
// 2. LÓGICA DO MODAL DE PESQUISA (GLOBAL)
// ============================================================

document.addEventListener('DOMContentLoaded', () => {
    console.log("MAIN.JS CARREGADO!"); // Debug

    const inputBusca = document.getElementById('inputBuscaModal');
    let timeoutBusca = null;

    if(inputBusca) {
        // Evento de digitação com "Debounce" (espera parar de digitar)
        inputBusca.addEventListener('input', function() {
            const termo = this.value;
            clearTimeout(timeoutBusca);
            
            if (termo.length < 2) return;
            
            timeoutBusca = setTimeout(async () => {
                try {
                    console.log("Buscando por:", termo); // Debug
                    const res = await fetch(`${API_URL}/api/produtos?nome=${encodeURIComponent(termo)}`);
                    
                    if(res.ok) {
                        const produtos = await res.json();
                        renderizarBusca(produtos);
                    }
                } catch(e) { 
                    console.error("Erro busca:", e); 
                }
            }, 300); // Espera 300ms após parar de digitar
        });
    }
});

/**
 * RENDERIZAÇÃO DA LISTA DE RESULTADOS
 * Verifica se quem chamou foi o CAIXA ou uma TELA DE CADASTRO
 */
function renderizarBusca(produtos) {
    console.log("Renderizando lista... Qtd:", produtos.length); // Debug

    const listaResultados = document.getElementById('listaResultadosModal');
    listaResultados.innerHTML = '';

    if (produtos.length === 0) {
        listaResultados.innerHTML = '<p style="padding:10px; text-align:center; color:#888;">Nenhum produto encontrado.</p>';
        return;
    }

    produtos.forEach(prod => {
        const div = document.createElement('div');
        
        // Estilos diretos para garantir funcionamento sem CSS externo
        div.style.padding = "10px";
        div.style.borderBottom = "1px solid #eee";
        div.style.cursor = "pointer";
        div.style.backgroundColor = "#fff";
        div.className = "item-produto-busca"; // Classe para CSS hover (opcional)
        
        div.innerHTML = `
            <div style="display: flex; justify-content: space-between;">
                <div>
                    <strong>${prod.nome}</strong><br>
                    <small style="color:#666">Cód: ${prod.codigo || prod.codProduto || 'ID: ' + prod.id}</small>
                </div>
                <div style="font-weight: bold; color: green;">
                    ${formatarMoeda(prod.valor)}
                </div>
            </div>
        `;

        // --- O CLIQUE MÁGICO (INTEGRAÇÃO COM CAIXA) ---
        div.onclick = function() { 
            console.log("CLIQUE DETECTADO NO PRODUTO:", prod.nome);

            // Verifica se o CAIXA definiu a função especial window.funcaoSelecaoProduto
            if (typeof window.funcaoSelecaoProduto === 'function') {
                console.log(">>> Enviando para o Carrinho do CAIXA");
                
                // Executa a função definida lá no caixa.js
                window.funcaoSelecaoProduto(prod);
            } 
            else {
                console.log(">>> Modo Padrão (Preencher Inputs de Etiqueta/Cadastro)");
                
                // Lógica antiga: Preencher formulários
                const display = document.getElementById('displayNomeProduto');
                const hidden = document.getElementById('etiquetaIdProduto');
                
                if(display) display.value = prod.nome;
                if(hidden) hidden.value = prod.id || prod.codProduto;
                
                // Fecha o modal
                document.getElementById('modalPesquisa').style.display = 'none';
            }
        };

        listaResultados.appendChild(div);
    });
}

// Função auxiliar para abrir o modal (usada por botões de pesquisa genéricos)
function abrirModalGlobal() {
    const modal = document.getElementById('modalPesquisa');
    const input = document.getElementById('inputBuscaModal');
    const lista = document.getElementById('listaResultadosModal');
    
    if(modal) {
        modal.style.display = 'flex';
        input.value = '';
        lista.innerHTML = '';
        input.focus();
    }
}

let origemPesquisa = ''; // Variável Global

function abrirModalPesquisa(origem) {
    origemPesquisa = origem; // Guarda quem chamou
    document.getElementById('modalPesquisa').style.display = 'flex'; // ou 'block'
    // ... foco no input ...
}

function selecionarProdutoPesquisa(id, nome, valor) {
    if (origemPesquisa === 'CAIXA') {
        // Preenche input do Caixa
        document.getElementById('caixaBuscaProduto').value = nome;
    } else if (origemPesquisa === 'RELATORIO') {
        // Preenche input do Relatório
        document.getElementById('displayNomeProduto').value = nome;
        document.getElementById('etiquetaIdProduto').value = id;
    }
    document.getElementById('modalPesquisa').style.display = 'none';
}

/* =================================================================================
   MÓDULO DE LOGIN E AUTENTICAÇÃO (INTEGRADO)
   ================================================================================= */

// Variável global para armazenar o usuário após o login
let usuarioLogado = null;

// 1. GARANTIA DE BLOQUEIO DE TELA (Ao carregar a página)
window.addEventListener('load', () => {
    console.log("Iniciando sistema de segurança...");

    const loginOverlay = document.getElementById('login-overlay');
    const body = document.body;

    if (loginOverlay) {
        // CORREÇÃO ESTRUTURAL: Move para a raiz do body se estiver aninhado errado
        if (loginOverlay.parentNode !== body) {
            body.appendChild(loginOverlay);
        }

        // CORREÇÃO VISUAL: Força o bloqueio da tela imediatamente
        // O !important via JS garante que sobrescreva qualquer CSS errado
        loginOverlay.style.setProperty('display', 'flex', 'important');
        loginOverlay.style.setProperty('z-index', '2147483647', 'important');
        loginOverlay.style.setProperty('visibility', 'visible', 'important');
        loginOverlay.style.setProperty('opacity', '1', 'important');

        // Remove a classe de desbloqueio caso ela tenha ficado em cache
        loginOverlay.classList.remove('unlocked');
        
        // Foco no campo de usuário
        const inputUser = document.getElementById('loginUsuario');
        if(inputUser) setTimeout(() => inputUser.focus(), 100);

    } else {
        console.error("ERRO CRÍTICO: Div 'login-overlay' não encontrada.");
        alert("Erro de segurança: Tela de login não carregada.");
    }
});

/**
 * 2. FUNÇÃO DE LOGIN REAL (Conectada ao Backend)
 */
async function realizarLoginVisual() {
    console.log("Tentativa de login iniciada...");

    const loginInput = document.getElementById('loginUsuario');
    const senhaInput = document.getElementById('loginSenha');
    const btnEntrar = document.querySelector('.btn-login-entrar');
    const loginOverlay = document.getElementById('login-overlay');

    // Validação Simples
    if (!loginInput.value || !senhaInput.value) {
        alert("Por favor, preencha usuário e senha.");
        return;
    }

    // A. Feedback Visual no Botão (Carregando)
    const textoOriginal = btnEntrar.innerHTML;
    if (btnEntrar) {
        btnEntrar.disabled = true;
        btnEntrar.innerHTML = '<i class="fas fa-circle-notch fa-spin"></i> Verificando...';
    }

    try {
        // B. Chamada ao Backend (Service que criamos)
        // Certifique-se que API_URL está declarada no início do arquivo (ex: http://localhost:8080)
        const response = await fetch(`${API_URL}/api/usuarios/login`, {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json' 
            },
            body: JSON.stringify({
                login: loginInput.value,
                senha: senhaInput.value
            })
        });

        // C. Processa a Resposta
        if (response.ok) {
            // --- SUCESSO ---
            const usuario = await response.json();
            usuarioLogado = usuario; // Salva na memória global
            console.log("Acesso autorizado para:", usuario.nome);

            // Animação de Sucesso (Igual a anterior)
            if (loginOverlay) {
                loginOverlay.classList.add('unlocked'); // Sobe a tela

                // Limpeza final após a animação (800ms)
                setTimeout(() => {
                    loginOverlay.style.display = 'none';
                    
                    // Opcional: Mostrar saudação
                    // alert(`Bem-vindo, ${usuario.nome}!`);
                }, 800);
            }

        } else {
            // --- ERRO (401 ou 403) ---
            const msgErro = await response.text();
            alert("Falha ao entrar: " + msgErro);
            
            // Restaura o botão
            if (btnEntrar) {
                btnEntrar.disabled = false;
                btnEntrar.innerHTML = textoOriginal;
            }
            // Limpa senha para tentar de novo
            senhaInput.value = '';
            senhaInput.focus();
        }

    } catch (error) {
        // --- ERRO DE CONEXÃO ---
        console.error("Erro técnico:", error);
        alert("Erro de conexão com o servidor. Verifique se o Backend está rodando.");
        
        if (btnEntrar) {
            btnEntrar.disabled = false;
            btnEntrar.innerHTML = textoOriginal;
        }
    }
}