/* =================================================================================
   5. MÓDULO DE RELATÓRIOS (ATUALIZADO)
   - Código de Barras
   - Etiquetas de Preço
   - Relatório de Vendas (PDF)
   ================================================================================= */

function setupReportEvents() {
    // --- BOTÕES DO MENU PRINCIPAL ---
    const btnGerarBarcode = document.getElementById('btnGerarBarcode');
    const btnEtiqueta = document.getElementById('btnAbrirEtiqueta');
    const btnVendas = document.getElementById('btnAbrirRelVendas'); // <--- NOVO
    const btnVoltar = document.getElementById('btnVoltarRelatorios');

    // --- LISTENERS DE NAVEGAÇÃO ---
    
    // 1. Código de Barras
    if (btnGerarBarcode) btnGerarBarcode.onclick = () => navegarRelatorios('barcode');

    // 2. Etiquetas
    if (btnEtiqueta) btnEtiqueta.onclick = () => navegarRelatorios('etiqueta');

    // 3. Relatório de Vendas (Novo)
    if (btnVendas) btnVendas.onclick = () => {
        navegarRelatorios('vendas');
        
        // Carrega a lista de produtos para o filtro checkbox
        carregarProdutosParaFiltro();
        
        // Define data inicial e final como "Hoje" por padrão
        const hoje = new Date();
        const inputInicio = document.getElementById('relDataInicio');
        const inputFim = document.getElementById('relDataFim');
        
        if(inputInicio) inputInicio.valueAsDate = hoje;
        if(inputFim) inputFim.valueAsDate = hoje;
    };

    // Botão Voltar (Comum a todos)
    if (btnVoltar) btnVoltar.onclick = () => navegarRelatorios('menu');


    // --- VALIDAÇÕES ESPECÍFICAS (BARCODE) ---
    const inputPosicao = document.getElementById('relatorioPosicao');
    if (inputPosicao) {
        inputPosicao.addEventListener('input', function() {
            let valor = parseInt(this.value);
            if (valor > 126) { alert("Máximo 126"); this.value = 126; }
            if (valor < 1 && this.value !== "") this.value = 1;
        });
    }

    // Submit dos formulários existentes (se houver lógica específica neles)
    const formBarcode = document.getElementById('formBarcode');
    if (formBarcode) {
        formBarcode.addEventListener('submit', handleRelatorioSubmit); // Função antiga se existir, ou pode remover se não usar
    }
}

/**
 * Controla telas da janela de Relatórios
 */
function navegarRelatorios(tela) {
    const views = {
        menu: document.getElementById('conteudoMenuRelatorios'),
        barcode: document.getElementById('conteudoFormBarcode'),
        etiqueta: document.getElementById('conteudoFormEtiqueta'),
        vendas: document.getElementById('conteudoFormVendas') // <--- NOVO
    };

    const titulo = document.getElementById('tituloJanelaRelatorios');
    const btnVoltar = document.getElementById('btnVoltarRelatorios');
    const icone = document.getElementById('iconeJanelaRelatorios');

    // Reseta todas as telas (Esconde tudo)
    Object.values(views).forEach(el => {
        if(el) el.style.display = 'none';
    });

    // Configuração Padrão
    icone.style.display = 'none';
    btnVoltar.style.display = 'inline-block';

    // Lógica de Exibição
    if (tela === 'menu') {
        views.menu.style.display = 'block';
        titulo.innerText = "Central de Relatórios";
        btnVoltar.style.display = 'none'; // No menu não tem voltar
        icone.style.display = 'inline-block';
    
    } else if (tela === 'barcode') {
        views.barcode.style.display = 'block';
        titulo.innerText = "Relatórios > Código de Barras";
    
    } else if (tela === 'etiqueta') {
        views.etiqueta.style.display = 'block';
        titulo.innerText = "Relatórios > Etiquetas";

    } else if (tela === 'vendas') { // <--- NOVO
        views.vendas.style.display = 'block';
        titulo.innerText = "Relatórios > Vendas Analítico";
    }
}

// =================================================================================
// FUNÇÕES AUXILIARES DO RELATÓRIO DE VENDAS
// =================================================================================

/**
 * Carrega produtos do backend e cria checkboxes para filtro múltiplo
 */
async function carregarProdutosParaFiltro() {
    const divLista = document.getElementById('listaProdutosCheck');
    if(!divLista) return;

    divLista.innerHTML = '<span style="font-size:12px; color:#666;">Carregando produtos...</span>';

    try {
        const res = await fetch(`${API_URL}/api/produtos`);
        if(!res.ok) throw new Error("Erro ao buscar produtos");

        const produtos = await res.json();
        divLista.innerHTML = '';
        
        if(produtos.length === 0) {
            divLista.innerHTML = '<span style="font-size:12px">Nenhum produto cadastrado.</span>';
            return;
        }
        
        produtos.forEach(prod => {
            const div = document.createElement('div');
            div.style.display = 'flex';
            div.style.alignItems = 'center';
            div.style.marginBottom = '2px';
            div.style.borderBottom = '1px solid #f0f0f0';
            
            // ID único para o checkbox
            const idCheck = `chk_prod_${prod.id || prod.codProduto}`;
            
            div.innerHTML = `
                <input type="checkbox" name="prodFiltro" value="${prod.id || prod.codProduto}" id="${idCheck}" checked style="margin-right: 8px; cursor: pointer;">
                <label for="${idCheck}" style="font-size: 13px; cursor: pointer; margin:0; width: 100%;">${prod.nome}</label>
            `;
            divLista.appendChild(div);
        });
    } catch (e) {
        console.error(e);
        divLista.innerHTML = '<span style="color:red; font-size:12px">Erro de conexão.</span>';
    }
}

/**
 * Marca ou Desmarca todos os checkboxes de produtos
 */
function marcarTodosProdutos(marcar) {
    const checkboxes = document.querySelectorAll('input[name="prodFiltro"]');
    checkboxes.forEach(chk => chk.checked = marcar);
}

/**
 * GERA O PDF DO RELATÓRIO DE VENDAS
 * - Busca dados, Filtra no Front (Data, Pagamento, Produtos) e Abre Janela de Impressão
 */
async function gerarRelatorioVendasPDF() {
    // 1. Captura Filtros
    const dataInicio = document.getElementById('relDataInicio').value;
    const dataFim = document.getElementById('relDataFim').value;
    const formaPagamento = document.getElementById('relFormaPagamento').value;
    
    // Pega IDs dos produtos selecionados
    const checkboxes = document.querySelectorAll('input[name="prodFiltro"]:checked');
    const produtosSelecionados = Array.from(checkboxes).map(c => String(c.value)); // Converte para String para garantir comparação

    // Validações
    if (!dataInicio || !dataFim) { alert("Selecione o período."); return; }
    if (produtosSelecionados.length === 0) { alert("Selecione pelo menos um produto."); return; }

    // Feedback visual no botão
    const btn = document.querySelector('#formRelatorioVendas button'); // O botão de gerar
    const txtOriginal = btn ? btn.innerHTML : 'Gerar';
    if(btn) btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processando...';

    try {
        // 2. Busca TODAS as vendas (Idealmente seria filtrado no backend, mas faremos aqui)
        const res = await fetch(`${API_URL}/api/vendas`); 
        if(!res.ok) throw new Error("Erro ao buscar vendas na API");
        
        const todasVendas = await res.json();

        // 3. FILTRAGEM AVANÇADA
        const vendasFiltradas = todasVendas.filter(venda => {
            // Formata data da venda para YYYY-MM-DD
            const dataVenda = new Date(venda.dataVenda).toISOString().split('T')[0];
            
            // A. Filtro Data
            if (dataVenda < dataInicio || dataVenda > dataFim) return false;

            // B. Filtro Pagamento (Verifica se houve valor > 0 na modalidade escolhida)
            if (formaPagamento !== "TODAS") {
                if (formaPagamento === "DINHEIRO" && (!venda.valorDinheiro || venda.valorDinheiro <= 0)) return false;
                if (formaPagamento === "PIX" && (!venda.valorPix || venda.valorPix <= 0)) return false;
                if (formaPagamento === "CREDITO" && (!venda.valorCredito || venda.valorCredito <= 0)) return false;
                if (formaPagamento === "DEBITO" && (!venda.valorDebito || venda.valorDebito <= 0)) return false;
            }

            // C. Filtro Produtos (A venda contém ALGUM dos produtos marcados?)
            // Verifica nos itens da venda
            const temProdutoSelecionado = venda.itens.some(item => {
                const idProdItem = String(item.codProduto || item.produto?.codProduto || item.produto?.id);
                return produtosSelecionados.includes(idProdItem);
            });
            
            return temProdutoSelecionado;
        });

        if (vendasFiltradas.length === 0) {
            alert("Nenhuma venda encontrada com esses filtros.");
            if(btn) btn.innerHTML = txtOriginal;
            return;
        }

        // 4. CONSTRUÇÃO DO HTML (Layout do PDF)
        let htmlConteudo = '';
        let totalGeralPeriodo = 0;

        vendasFiltradas.forEach(venda => {
            totalGeralPeriodo += venda.valorTotal;
            
            const dataF = new Date(venda.dataVenda).toLocaleString('pt-BR');

            // Detalhes Pagamento (Quais meios foram usados nesta venda)
            let pagtos = [];
            if(venda.valorDinheiro > 0) pagtos.push(`Dinheiro`);
            if(venda.valorPix > 0) pagtos.push(`PIX`);
            if(venda.valorCredito > 0) pagtos.push(`Crédito`);
            if(venda.valorDebito > 0) pagtos.push(`Débito`);
            
            const textoPagto = pagtos.length > 0 ? pagtos.join(', ') : 'Não informado';

            // Monta HTML da Venda
            htmlConteudo += `
                <div class="venda-box">
                    <div class="venda-header">
                        <span><strong>Venda #${venda.codVenda || venda.id}</strong> <small>(${dataF})</small></span>
                        <span>Total: <strong>${formatarMoeda(venda.valorTotal)}</strong></span>
                    </div>
                    <div class="venda-info">
                        Forma(s): ${textoPagto} | Itens: ${venda.itens.length}
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

        // 5. ABRE JANELA DE IMPRESSÃO
        const janela = window.open('', '', 'width=900,height=600');
        janela.document.write(`
            <html>
            <head>
                <title>Relatório de Vendas - Horus</title>
                <style>
                    body { font-family: 'Segoe UI', sans-serif; padding: 20px; font-size: 12px; color: #333; }
                    .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #333; padding-bottom: 10px; }
                    .header h1 { margin: 0; font-size: 18px; text-transform: uppercase; }
                    .filtros { margin-bottom: 20px; background: #f8f9fa; padding: 10px; border-radius: 4px; border: 1px solid #ddd; font-size: 11px; }
                    
                    .venda-box { border: 1px solid #ccc; margin-bottom: 15px; page-break-inside: avoid; border-radius: 4px; overflow: hidden; }
                    .venda-header { background: #e9ecef; padding: 8px 10px; display: flex; justify-content: space-between; border-bottom: 1px solid #ddd; }
                    .venda-info { padding: 4px 10px; font-size: 10px; color: #666; background: #fff; border-bottom: 1px solid #eee; }
                    
                    .tabela-itens { width: 100%; border-collapse: collapse; }
                    .tabela-itens th { background: #fff; text-align: left; padding: 4px 10px; font-size: 10px; color: #888; border-bottom: 1px solid #eee; }
                    .tabela-itens td { padding: 4px 10px; border-bottom: 1px solid #f9f9f9; font-size: 11px; }
                    .tabela-itens tr:last-child td { border-bottom: none; }
                    
                    .total-geral { text-align: right; font-size: 16px; margin-top: 20px; font-weight: bold; padding: 10px; background: #333; color: #fff; border-radius: 4px; }
                    
                    @media print {
                        body { padding: 0; }
                        .no-print { display: none; }
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>Relatório Analítico de Vendas</h1>
                    <small>Sistema Horus</small>
                </div>
                
                <div class="filtros">
                    <b>Período:</b> ${dataInicio.split('-').reverse().join('/')} até ${dataFim.split('-').reverse().join('/')} <br>
                    <b>Pagamento:</b> ${formaPagamento} <br>
                    <b>Emissão:</b> ${new Date().toLocaleString()}
                </div>

                ${htmlConteudo}

                <div class="total-geral">
                    TOTAL VENDIDO NO PERÍODO: ${formatarMoeda(totalGeralPeriodo)}
                </div>

                <script>
                    // Manda imprimir assim que carregar
                    window.onload = function() { window.print(); }
                </script>
            </body>
            </html>
        `);
        janela.document.close();

    } catch (e) {
        console.error(e);
        alert("Erro ao gerar relatório: " + e.message);
    } finally {
        if(btn) btn.innerHTML = txtOriginal;
    }
}