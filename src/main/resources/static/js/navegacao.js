/* =================================================================================
   4. MÓDULO DE PRODUTOS (NAVEGAÇÃO, CADASTRO E CONSULTA)
   ================================================================================= */

function setupProductEvents() {
    // --- NAVEGAÇÃO INTERNA ---
    const btnNovo = document.getElementById('btnNovoProduto');
    const btnVoltar = document.getElementById('btnVoltarProdutos');
    
    // Seleciona o botão Consultar (que é o segundo botão do menu)
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

/**
 * Controla as telas dentro da janela de Produtos (Menu, Cadastro, Consulta)
 */
function navegarProdutos(tela) {
    // Elementos de UI
    const views = {
        menu: document.getElementById('conteudoMenuProdutos'),
        cadastro: document.getElementById('conteudoFormProdutos'),
        consulta: document.getElementById('conteudoConsultaProdutos')
    };
    const titulo = document.getElementById('tituloJanelaProdutos');
    const btnVoltar = document.getElementById('btnVoltarProdutos');
    const icone = document.getElementById('iconeJanelaPrincipal');

    // Reseta tudo
    Object.values(views).forEach(el => el.style.display = 'none');

    // Ativa a tela desejada
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
        views.consulta.style.display = 'flex'; // Flex para layout da tabela
        titulo.innerText = "Produtos > Consultar";
        btnVoltar.style.display = 'inline-block';
        icone.style.display = 'none';
        // Auto-focus no campo de busca
        setTimeout(() => document.getElementById('filtroNomeProduto').focus(), 100);
    }
}

/**
 * Lógica de Busca na API e Preenchimento da Tabela
 */
async function buscarProdutosAPI() {
    const termo = document.getElementById('filtroNomeProduto').value;
    const tbody = document.getElementById('tabelaProdutosCorpo');
    
    // Mostra loading
    tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; padding: 20px;">Buscando...</td></tr>';

    try {
        const url = termo 
            ? `${API_URL}/api/produtos?nome=${encodeURIComponent(termo)}` 
            : `${API_URL}/api/produtos`; 
        
        const response = await fetch(url);
        
        if (response.ok) {
            const lista = await response.json();
            preencherTabelaProdutos(lista);
        } else {
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:red; padding: 20px;">Nenhum produto encontrado ou erro na busca.</td></tr>';
        }
    } catch (error) {
        console.error(error);
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:red; padding: 20px;">Erro de conexão com o servidor.</td></tr>';
    }
}

/**
 * Renderiza as linhas da tabela de produtos
 */
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
    // Confirmação nativa (simples e eficaz)
    if (!confirm("Tem certeza que deseja excluir este produto? Esta ação não pode ser desfeita.")) {
        return;
    }

    try {
        const response = await fetch(`${API_URL}/api/produtos/${id}`, {
            method: 'DELETE'
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
        // 1. Busca os dados atualizados do produto
        const response = await fetch(`${API_URL}/api/produtos/${id}`); 
        if (!response.ok) throw new Error('Erro ao buscar produto');
        
        const produto = await response.json();

        // 2. Preenche o formulário
        document.getElementById('produtoId').value = produto.codProduto; // ID Oculto
        document.getElementById('produtoCodigo').value = produto.codigo;
        document.getElementById('produtoNome').value = produto.nome;
        document.getElementById('produtoValor').value = produto.valor;

        // 3. Troca de telas (Esconde lista, mostra formulário)
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
    
    // Configura o evento do formulário DE FORMA BLINDADA (onsubmit)
    const form = document.getElementById('formSalvarProduto');
    if (form) {
        form.onsubmit = handleSalvarProduto;
    }

    // Configura o botão "Novo Produto"
    const btnNovo = document.getElementById('btnNovoProduto');
    if (btnNovo) {
        btnNovo.addEventListener('click', () => {
            document.getElementById('formSalvarProduto').reset();
            document.getElementById('produtoId').value = ""; // LIMPA O ID (Modo Criação)
            mostrarTelaFormulario();
        });
    }

    // Configura o botão Voltar
    const btnVoltar = document.getElementById('btnVoltarProdutos');
    if (btnVoltar) {
        btnVoltar.addEventListener('click', mostrarTelaLista);
    }
});

async function handleSalvarProduto(e) {
    e.preventDefault();

    // Captura os dados
    const id = document.getElementById('produtoId').value;
    const codigo = document.getElementById('produtoCodigo').value;
    const nome = document.getElementById('produtoNome').value;
    
    // Tratamento de conversão de moeda
    const valorFormatado = document.getElementById('produtoValor').value;
    const valorParaBackend = valorFormatado
        ? parseFloat(valorFormatado.replace(/[^\d,]/g, '').replace(',', '.'))
        : 0.0;

    // Monta o objeto
    const produtoData = {
        codigo: codigo,
        nome: nome,
        valor: valorParaBackend
    };

    try {
        let url;
        let method;

        if (id) {
            // --- MODO EDIÇÃO (PUT) ---
            url = `${API_URL}/api/produtos/${id}`;
            method = 'PUT';
        } else {
            // --- MODO CRIAÇÃO (POST) ---
            url = `${API_URL}/api/produtos`;
            method = 'POST';
        }

        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(produtoData)
        });

        if (response.ok) {
            alert(id ? "Produto atualizado com sucesso!" : "Produto cadastrado com sucesso!");
            
            // Limpa o form e o ID oculto
            e.target.reset();
            document.getElementById('produtoId').value = "";

            mostrarTelaLista(); // Volta para a tela inicial
            buscarProdutosAPI(); // Recarrega a tabela com os dados novos
        } else {
            const errorText = await response.text();
            alert("Erro ao salvar: " + errorText);
        }

    } catch (error) {
        console.error(error);
        alert("Erro de conexão.");
    }
}

function mostrarTelaFormulario() {
    document.getElementById('conteudoConsultaProdutos').style.display = 'none'; // Esconde Lista
    document.getElementById('conteudoFormProdutos').style.display = 'block';    // Mostra Form
    document.getElementById('btnVoltarProdutos').style.display = 'inline-block'; // Mostra Seta Voltar
    document.getElementById('btnNovoProduto').style.display = 'none'; // Esconde botão Novo
}

function mostrarTelaLista() {
    document.getElementById('conteudoFormProdutos').style.display = 'none';    // Esconde Form
    document.getElementById('conteudoConsultaProdutos').style.display = 'flex'; // Mostra Lista
    document.getElementById('btnVoltarProdutos').style.display = 'none'; // Esconde Seta Voltar
    document.getElementById('btnNovoProduto').style.display = 'inline-block'; // Mostra botão Novo
}