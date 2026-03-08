/* =================================================================================
   4. MÓDULO DE PRODUTOS (NAVEGAÇÃO, CADASTRO E CONSULTA)
   ================================================================================= */

function setupProductEvents() {
    const btnNovo = document.getElementById('btnNovoProduto');
    const btnVoltar = document.getElementById('btnVoltarProdutos');
    const botoesMenu = document.querySelectorAll('#conteudoMenuProdutos .botaoJanela');
    const btnConsultar = botoesMenu[1]; 

    if (btnNovo) btnNovo.onclick = () => navegarProdutos('cadastro');
    if (btnConsultar) btnConsultar.onclick = () => navegarProdutos('consulta');
    if (btnVoltar) btnVoltar.onclick = () => navegarProdutos('menu');

    // --- CONSULTA (PESQUISA) ---
    const btnPesquisar = document.getElementById('btnPesquisarProduto');
    const inputFiltro = document.getElementById('filtroNomeProduto');
    
    if (btnPesquisar) btnPesquisar.onclick = buscarProdutosAPI;
    if (inputFiltro) {
        inputFiltro.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') buscarProdutosAPI();
        });
    }
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

    Object.values(views).forEach(el => el.style.display = 'none');

    if (tela === 'menu') {
        views.menu.style.display = 'block';
        titulo.innerText = "Cadastro Geral de Produtos";
        btnVoltar.style.display = 'none';
        icone.style.display = 'inline-block';
    } else if (tela === 'cadastro') {
        views.cadastro.style.display = 'block';
        titulo.innerText = "Produtos > Cadastrar";
        btnVoltar.style.display = 'inline-block';
        icone.style.display = 'none';
    } else if (tela === 'consulta') {
        views.consulta.style.display = 'flex'; 
        titulo.innerText = "Produtos > Consultar";
        btnVoltar.style.display = 'inline-block';
        icone.style.display = 'none';
        setTimeout(() => document.getElementById('filtroNomeProduto').focus(), 100);
    }
}

async function buscarProdutosAPI() {
    const termo = document.getElementById('filtroNomeProduto').value;
    const tbody = document.getElementById('tabelaProdutosCorpo');
    const token = localStorage.getItem('tokenHorus'); 
    
    tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; padding: 20px;">Buscando...</td></tr>';

    try {
        const url = termo 
            ? `${API_URL}/api/produtos?nome=${encodeURIComponent(termo)}` 
            : `${API_URL}/api/produtos`; 
            
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}` 
            }
        });
        
        if (response.ok) {
            const lista = await response.json();
            preencherTabelaProdutos(lista);
        } else {
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:red; padding: 20px;">Nenhum produto encontrado ou acesso negado.</td></tr>';
        }
    } catch (error) {
        console.error(error);
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:red; padding: 20px;">Erro de conexão com o servidor.</td></tr>';
    }
}

function preencherTabelaProdutos(lista) {
    const tbody = document.getElementById('tabelaProdutosCorpo');
    tbody.innerHTML = ''; 

    if (!lista || lista.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; padding: 20px;">Nenhum registro encontrado.</td></tr>';
        return;
    }

    const formatador = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

    lista.forEach(prod => {
        const idReal = prod.id || prod.codProduto; 

        const tr = document.createElement('tr');
        tr.style.borderBottom = '1px solid #eee';

        tr.innerHTML = `
            <td style="padding: 10px;">${prod.codigo || '---'}</td>
            <td style="padding: 10px;">${prod.nome}</td>
            <td style="padding: 10px; text-align: right;">${prod.valor ? formatador.format(prod.valor) : 'R$ 0,00'}</td>
            <td style="padding: 10px; text-align: center;">
                <button class="btn-editar" onclick="prepararEdicao(${idReal})" title="Editar">
                    <i class="fas fa-edit"></i>
                </button>
                <button class="btn-excluir" onclick="deletarProduto(${idReal})" title="Excluir">
                    <i class="fas fa-trash-alt"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function deletarProduto(id) {
    if (!confirm("Tem certeza que deseja excluir este produto? Esta ação não pode ser desfeita.")) {
        return;
    }

    const token = localStorage.getItem('tokenHorus');

    try {
        const response = await fetch(`${API_URL}/api/produtos/${id}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok || response.status === 204) {
            buscarProdutosAPI(); 
        } else {
            alert("Erro ao excluir. O produto pode estar vinculado a vendas.");
        }
    } catch (error) {
        console.error(error);
        alert("Erro de conexão ao tentar excluir.");
    }
}

async function prepararEdicao(id) {
    try {
        const token = localStorage.getItem('tokenHorus');
        const response = await fetch(`${API_URL}/api/produtos/${id}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        }); 
        
        if (!response.ok) throw new Error('Erro ao buscar produto');
        
        const produto = await response.json();

        // CORREÇÃO 1: Garante que pega o ID correto independente de como o Java chamou
        const idReal = produto.id || produto.codProduto;
        
        // CORREÇÃO 2: Formata o valor do Java (150.50) para o padrão brasileiro do input (150,50)
        const valorFormatado = produto.valor ? produto.valor.toFixed(2).replace('.', ',') : '';

        // Preenche o formulário
        document.getElementById('produtoId').value = idReal; 
        document.getElementById('produtoCodigo').value = produto.codigo || '';
        document.getElementById('produtoNome').value = produto.nome || '';
        document.getElementById('produtoValor').value = valorFormatado;

        mostrarTelaFormulario();

    } catch (error) {
        console.error(error);
        alert("Erro ao carregar dados para edição.");
    }
}

/* =================================================================================
   INICIALIZAÇÃO E SUBMIT UNIFICADO
   ================================================================================= */
document.addEventListener('DOMContentLoaded', () => {
    
    const form = document.getElementById('formSalvarProduto');
    if (form) {
        form.onsubmit = handleSalvarProduto;
    }

    const btnNovo = document.getElementById('btnNovoProduto');
    if (btnNovo) {
        btnNovo.addEventListener('click', () => {
            document.getElementById('formSalvarProduto').reset();
            document.getElementById('produtoId').value = ""; // LIMPA O ID PARA O POST FUNCIONAR
            mostrarTelaFormulario();
        });
    }

    const btnVoltar = document.getElementById('btnVoltarProdutos');
    if (btnVoltar) {
        btnVoltar.addEventListener('click', mostrarTelaLista);
    }
});

async function handleSalvarProduto(e) {
    e.preventDefault();

    const id = document.getElementById('produtoId').value;
    const codigo = document.getElementById('produtoCodigo').value;
    const nome = document.getElementById('produtoNome').value;
    
    const valorFormatado = document.getElementById('produtoValor').value;
    const valorParaBackend = valorFormatado
        ? parseFloat(valorFormatado.replace(/[^\d,]/g, '').replace(',', '.'))
        : 0.0;

    const produtoData = {
        codigo: codigo,
        nome: nome,
        valor: valorParaBackend
    };

    const token = localStorage.getItem('tokenHorus');

    try {
        let url;
        let method;

        if (id) {
            // Se tem ID, atualiza (PUT)
            url = `${API_URL}/api/produtos/${id}`;
            method = 'PUT';
        } else {
            // Se não tem ID, cria (POST)
            url = `${API_URL}/api/produtos`;
            method = 'POST';
        }

        const response = await fetch(url, {
            method: method,
            headers: { 
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}` 
            },
            body: JSON.stringify(produtoData)
        });

        if (response.ok) {
            alert(id ? "Produto atualizado com sucesso!" : "Produto cadastrado com sucesso!");
            
            e.target.reset();
            document.getElementById('produtoId').value = "";

            mostrarTelaLista(); 
            buscarProdutosAPI(); 
        } else {
            const errorText = await response.text();
            alert("Erro ao salvar: " + errorText);
        }

    } catch (error) {
        console.error("Erro no fetch de salvar produto:", error);
        alert("Erro de conexão ao salvar.");
    }
}

function mostrarTelaFormulario() {
    document.getElementById('conteudoConsultaProdutos').style.display = 'none'; 
    document.getElementById('conteudoFormProdutos').style.display = 'block';    
    document.getElementById('btnVoltarProdutos').style.display = 'inline-block'; 
    document.getElementById('btnNovoProduto').style.display = 'none'; 
}

function mostrarTelaLista() {
    document.getElementById('conteudoFormProdutos').style.display = 'none';    
    document.getElementById('conteudoConsultaProdutos').style.display = 'flex'; 
    document.getElementById('btnVoltarProdutos').style.display = 'none'; 
    document.getElementById('btnNovoProduto').style.display = 'inline-block'; 
}