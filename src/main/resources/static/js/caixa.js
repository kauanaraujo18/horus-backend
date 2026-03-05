/**
 * Lógica da Frente de Caixa (PDV)
 * Atualizado com Leitor de Código de Barras
 */

let carrinho = [];

// ============================================================
// 1. EVENTOS E INICIALIZAÇÃO
// ============================================================

// Atalho de Teclado (F2) para abrir pesquisa
document.addEventListener('keydown', function(e) {
    const caixaDiv = document.getElementById('caixaDiv');
    
    // Só ativa se a janela do caixa estiver visível na tela
    if (caixaDiv && caixaDiv.style.display !== 'none' && e.key === 'F2') {
        e.preventDefault();
        abrirModalPesquisaParaCaixa();
    }
});

// EVENTO DO LEITOR DE CÓDIGO DE BARRAS (Enter)
const inputCodigo = document.getElementById('inputCodigoBarras');
if (inputCodigo) {
    inputCodigo.addEventListener('keydown', async function(e) {
        if (e.key === 'Enter') {
            e.preventDefault(); // Evita submit de formulário padrão
            const codigo = this.value.trim();
            
            if (codigo) {
                await processarCodigoBarras(codigo);
            }
        }
    });
}

// ============================================================
// 2. LÓGICA DO LEITOR DE BARRAS
// ============================================================

function toggleLeitorBarras() {
    const painel = document.getElementById('painelLeitorBarras');
    const input = document.getElementById('inputCodigoBarras');
    
    if (painel.style.display === 'none') {
        // Abrir e focar
        painel.style.display = 'block';
        input.value = '';
        input.focus(); 
    } else {
        // Fechar
        painel.style.display = 'none';
        input.value = '';
    }
}

async function processarCodigoBarras(codigo) {
    const input = document.getElementById('inputCodigoBarras');
    input.disabled = true; // Trava o input

    try {
        // MUDANÇA AQUI: Chamamos a nova rota específica de código
        // Ex: http://localhost:8080/api/produtos/codigo/789123456
        const res = await fetch(`${API_URL}/api/produtos/codigo/${encodeURIComponent(codigo)}`);
        
        if (res.ok) {
            // Se deu status 200, é porque achou UM produto
            const produtoExato = await res.json();
            
            // Adiciona direto com quantidade 1 (automático)
            adicionarAoCarrinho(produtoExato, 1);
            
            // Limpa e foca para o próximo
            input.value = '';
            input.disabled = false;
            input.focus();
            
        } else if (res.status === 404) {
            // Se deu status 404, não existe
            alert("Produto não encontrado com este código: " + codigo);
            input.value = ''; 
            input.disabled = false;
            input.focus();
        } else {
            console.error("Erro desconhecido na API");
            input.disabled = false;
            input.focus();
        }
    } catch (error) {
        console.error(error);
        alert("Erro de conexão ao buscar código.");
        input.disabled = false;
        input.focus();
    }
}

// ============================================================
// 3. INTEGRAÇÃO COM MODAL DE PESQUISA (MANUAL)
// ============================================================

function abrirModalPesquisaParaCaixa() {
    // 1. Abre o modal (usa função global se existir)
    if (typeof abrirModalGlobal === 'function') {
        abrirModalGlobal();
    } else {
        // Fallback manual
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

    // 2. DEFINE A GLOBAL PARA O MAIN.JS ENXERGAR
    console.log("Definindo função de seleção para o Caixa");
    
    window.funcaoSelecaoProduto = function(produto) {
        console.log("Callback recebido no caixa:", produto);
        
        // Chama a adição ao array (sem parametro de qtd, vai pedir prompt)
        adicionarAoCarrinho(produto);
        
        // Fecha o modal
        const modal = document.getElementById('modalPesquisa');
        if(modal) modal.style.display = 'none';
        
        // Reseta a função global
        window.funcaoSelecaoProduto = null; 
        
        // Devolve o foco para o campo de busca manual do caixa
        const campoBusca = document.getElementById('caixaBuscaProduto');
        if(campoBusca) campoBusca.focus();
    };
}

// ============================================================
// 4. GERENCIAMENTO DO CARRINHO
// ============================================================

/**
 * Adiciona produto ao carrinho.
 * @param {Object} produto - Objeto do produto vindo da API.
 * @param {Number|null} qtdAutomatica - Se informado, pula o prompt (usado pelo leitor).
 */
function adicionarAoCarrinho(produto, qtdAutomatica = null) {
    let qtd;

    if (qtdAutomatica !== null) {
        // MODO AUTOMÁTICO (Leitor de Código)
        qtd = qtdAutomatica;
    } else {
        // MODO MANUAL (Pesquisa por Nome)
        let qtdInput = prompt(`Adicionar "${produto.nome}"\nQuantidade:`, "1");
        if (qtdInput === null) return; // Cancelou
        qtd = parseInt(qtdInput);
    }
    
    // Validação
    if (isNaN(qtd) || qtd <= 0) {
        alert("Quantidade inválida.");
        return;
    }

    // Normaliza o ID
    const idProduto = produto.id || produto.codProduto || produto.cod_produto;

    // Verifica se o produto já está no carrinho
    const indexExistente = carrinho.findIndex(item => item.codProduto == idProduto);

    if (indexExistente >= 0) {
        // Se já existe, soma a quantidade
        carrinho[indexExistente].quantidade += qtd;
        
        // Feedback no console para debug
        if(qtdAutomatica) console.log(`+${qtd} unid. adicionada ao item existente.`);
    } else {
        // Se não existe, adiciona novo objeto
        carrinho.push({
            codProduto: idProduto,
            nome: produto.nome,
            valorUnitario: produto.valor,
            quantidade: qtd
        });
    }

    renderizarCarrinho();
}

function removerDoCarrinho(index) {
    carrinho.splice(index, 1);
    renderizarCarrinho();
}

function renderizarCarrinho() {
    const tbody = document.getElementById('tabelaCarrinho');
    tbody.innerHTML = ''; 

    let subtotalCarrinho = 0;

    carrinho.forEach((item, index) => {
        const totalItem = item.quantidade * item.valorUnitario;
        subtotalCarrinho += totalItem;

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td style="padding: 10px;">
                <span style="font-weight: 500;">${item.nome}</span>
                ${item.quantidade > 1 ? '<br><small style="color:#28a745; font-size:10px">Item acumulado</small>' : ''}
            </td>
            <td style="padding: 10px; text-align: center;">
                ${item.quantidade}
            </td>
            <td style="padding: 10px; text-align: right;">
                ${formatarMoeda(item.valorUnitario)}
            </td>
            <td style="padding: 10px; text-align: right; font-weight: bold;">
                ${formatarMoeda(totalItem)}
            </td>
            <td style="text-align: center;">
                <button onclick="removerDoCarrinho(${index})" class="btn-excluir" style="color: red;" title="Remover item">
                    <i class="fas fa-trash-alt"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });

    document.getElementById('lblSubtotal').innerText = formatarMoeda(subtotalCarrinho);
    calcularTotaisCaixa();
}

// ============================================================
// 5. CÁLCULOS FINANCEIROS
// ============================================================

function calcularTotaisCaixa() {
    // 1. CÁLCULO DA VENDA (Subtotal, Desconto, Acréscimo)
    const subtotal = carrinho.reduce((acc, item) => acc + (item.quantidade * item.valorUnitario), 0);
    const valDesconto = converterMoedaParaFloat(document.getElementById('inputDesconto').value);
    const valAcrescimo = converterMoedaParaFloat(document.getElementById('inputAcrescimo').value);

    const totalFinalVenda = subtotal + valAcrescimo - valDesconto;

    // Atualiza Labels da Venda
    document.getElementById('lblSubtotal').innerText = formatarMoeda(subtotal);
    const lblTotal = document.getElementById('lblTotalFinal');
    lblTotal.innerText = formatarMoeda(totalFinalVenda);
    
    // Muda cor do total final
    lblTotal.style.color = totalFinalVenda < 0 ? '#dc3545' : '#28a745';

    // 2. SOMA DOS PAGAMENTOS LANÇADOS
    const vDin = converterMoedaParaFloat(document.getElementById('inputPagDinheiro').value);
    const vPix = converterMoedaParaFloat(document.getElementById('inputPagPix').value);
    const vCred = converterMoedaParaFloat(document.getElementById('inputPagCredito').value);
    const vDeb = converterMoedaParaFloat(document.getElementById('inputPagDebito').value);

    const totalPago = vDin + vPix + vCred + vDeb;

    // Atualiza o input readonly "Valor Pago Total"
    document.getElementById('inputValorPago').value = formatarMoeda(totalPago);

    // 3. LÓGICA DA BALANÇA (FALTA vs TROCO)
    let diferenca = totalPago - totalFinalVenda;
    
    let faltaPagar = 0;
    let troco = 0;

    if (diferenca < 0) {
        // Se a diferença é negativa, falta dinheiro (transformamos em positivo para exibir)
        faltaPagar = Math.abs(diferenca);
        troco = 0;
    } else {
        // Se a diferença é positiva ou zero, já pagou tudo e sobra troco
        faltaPagar = 0;
        troco = diferenca;
    }

    // Atualiza os Labels Visuais
    document.getElementById('lblAreceber').innerText = formatarMoeda(faltaPagar);
    document.getElementById('lblTroco').innerText = formatarMoeda(troco);
}

// ============================================================
// 6. FINALIZAÇÃO DA VENDA
// ============================================================

async function finalizarVenda() {
    if (carrinho.length === 0) {
        alert("Carrinho vazio.");
        return;
    }

    // Recalcula totais
    const subtotal = carrinho.reduce((acc, item) => acc + (item.quantidade * item.valorUnitario), 0);
    const desc = converterMoedaParaFloat(document.getElementById('inputDesconto').value);
    const acresc = converterMoedaParaFloat(document.getElementById('inputAcrescimo').value);
    const totalFinal = subtotal + acresc - desc;

    // Pega os valores individuais
    const vDin = converterMoedaParaFloat(document.getElementById('inputPagDinheiro').value);
    const vPix = converterMoedaParaFloat(document.getElementById('inputPagPix').value);
    const vCred = converterMoedaParaFloat(document.getElementById('inputPagCredito').value);
    const vDeb = converterMoedaParaFloat(document.getElementById('inputPagDebito').value);
    
    const totalPago = vDin + vPix + vCred + vDeb;

    // Validação: Se pagou menos que o total
    // Nota: Permite totalPago = 0 se você quiser permitir venda fiado, senão mantenha a validação abaixo
    if (totalPago < totalFinal) {
        alert(`Pagamento insuficiente!\nTotal Venda: ${formatarMoeda(totalFinal)}\nPago: ${formatarMoeda(totalPago)}\nFalta: ${formatarMoeda(totalFinal - totalPago)}`);
        return;
    }

    const btn = document.querySelector('#caixaDiv button[onclick="finalizarVenda()"]');
    const txtOriginal = btn.innerHTML;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ...';
    btn.disabled = true;

    try {
        const vendaDTO = {
            qtdParcelas: parseInt(document.getElementById('inputParcelas').value) || 1,
            desconto: desc,
            acrescimo: acresc,
            
            // Novos campos discriminados
            valorDinheiro: vDin,
            valorPix: vPix,
            valorCredito: vCred,
            valorDebito: vDeb,
            
            // O Backend vai somar isso para definir o valorPago e Troco
            
            itens: carrinho.map(item => ({
                codProduto: item.codProduto,
                quantidade: item.quantidade,
                valorUnitario: item.valorUnitario
            }))
        };

        const response = await fetch(`${API_URL}/api/vendas`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(vendaDTO)
        });

        if (response.ok) {
            const msg = await response.text();
            alert("✅ Venda realizada!\n" + msg);
            
            // Limpa tudo
            carrinho = [];
            renderizarCarrinho();
            document.getElementById('inputDesconto').value = '';
            document.getElementById('inputAcrescimo').value = '';
            document.getElementById('inputParcelas').value = '1';
            
            // Limpa campos de pagamento
            document.getElementById('inputPagDinheiro').value = '';
            document.getElementById('inputPagPix').value = '';
            document.getElementById('inputPagCredito').value = '';
            document.getElementById('inputPagDebito').value = '';
            
            // Zera totais
            calcularTotaisCaixa();
            
            // Foco
            const leitor = document.getElementById('inputCodigoBarras');
            if(leitor && document.getElementById('painelLeitorBarras').style.display !== 'none') leitor.focus();
            else document.getElementById('caixaBuscaProduto').focus();

        } else {
            const erro = await response.text();
            alert("Erro: " + erro);
        }
    } catch (e) {
        console.error(e);
        alert("Erro de conexão.");
    } finally {
        btn.innerHTML = txtOriginal;
        btn.disabled = false;
    }
}