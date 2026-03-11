

document.addEventListener('DOMContentLoaded', function() {
    
    // Elementos do DOM
    const btnAbrirModal = document.getElementById('btnAbrirModal');
    const modal = document.getElementById('modalPesquisa');
    const btnFecharModal = document.querySelector('.close-modal');
    const inputBuscaModal = document.getElementById('inputBuscaModal');
    const listaResultados = document.getElementById('listaResultadosModal');
    
    // Elementos do Formulário Principal
    const displayNome = document.getElementById('displayNomeProduto');
    const inputIdOculto = document.getElementById('etiquetaIdProduto');
    let timeoutBusca = null;

    // --- 1. ABRIR E FECHAR MODAL ---
    
    if(btnAbrirModal) {
        btnAbrirModal.addEventListener('click', function() {
            modal.style.display = 'flex'; // Mostra o modal
            inputBuscaModal.value = '';   // Limpa busca anterior
            listaResultados.innerHTML = '<p style="color:#999; text-align:center; padding:10px">Digite para buscar...</p>';
            setTimeout(() => inputBuscaModal.focus(), 100); // Foca no campo automaticamente
        });
    }

    // Fechar no X
    if(btnFecharModal) {
        btnFecharModal.addEventListener('click', () => {
            modal.style.display = 'none';
        });
    }

    // Fechar clicando fora da janela branca
    window.addEventListener('click', (e) => {
        if (e.target == modal) {
            modal.style.display = 'none';
        }
    });

    // --- 2. LÓGICA DE BUSCA DENTRO DO MODAL ---
    
    if(inputBuscaModal) {
        inputBuscaModal.addEventListener('input', function() {
            const termo = this.value;
            clearTimeout(timeoutBusca); // Debounce

            if (termo.length < 2) {
                return;
            }

            // Espera 300ms antes de chamar o servidor
            timeoutBusca = setTimeout(() => {
                buscarProdutos(termo);
            }, 300);
        });
    }

    async function buscarProdutos(termo) {
        listaResultados.innerHTML = '<p style="text-align:center">Buscando...</p>';
        
        try {
            const response = await fetch(`${API_URL}/api/produtos/pesquisar?termo=${encodeURIComponent(termo)}`);
            if(response.ok) {
                const produtos = await response.json();
                renderizarLista(produtos);
            }
        } catch (error) {
            console.error(error);
            listaResultados.innerHTML = '<p style="color:red; text-align:center">Erro ao buscar.</p>';
        }
    }

    function renderizarLista(produtos) {
        listaResultados.innerHTML = '';

        if (produtos.length === 0) {
            listaResultados.innerHTML = '<p style="text-align:center; padding:10px">Nenhum produto encontrado.</p>';
            return;
        }

        produtos.forEach(prod => {
            const div = document.createElement('div');
            div.className = 'item-produto';
            div.innerHTML = `
                <strong>${prod.nome}</strong><br>
                <small style="color:#666">Cód: ${prod.codigo || 'S/N'} | ID: ${prod.codProduto}</small>
            `;

            // --- 3. SELEÇÃO DO PRODUTO ---
            div.addEventListener('click', () => {
                // Preenche o formulário principal atrás do modal
                displayNome.value = prod.nome;
                inputIdOculto.value = prod.codProduto;
                
                // Fecha o modal
                modal.style.display = 'none';
            });

            listaResultados.appendChild(div);
        });
    }
});

// --- 4. ENVIO DO FORMULÁRIO (Igual ao anterior) ---
// (Mantenha a função handleEtiquetaSubmit igual à resposta anterior)
async function handleEtiquetaSubmit(e) {
    e.preventDefault(); 
    const idProduto = document.getElementById('etiquetaIdProduto').value;
    const qtdCopias = document.getElementById('etiquetaQtdCopias').value;
    
    if (!idProduto) {
        alert("Selecione um produto primeiro!");
        return;
    }
    
    const btnSubmit = e.target.querySelector('button[type="submit"]');
    const textoOriginal = btnSubmit.innerHTML;
    btnSubmit.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Gerando...';
    
    // --- 1. Resgata o Token do armazenamento local ---
    const token = localStorage.getItem('tokenHorus');
    
    try {
        // --- 2. Injeta o cabeçalho de Autorização no fetch ---
        const response = await fetch(`${API_URL}/api/etiquetas/gerar/${idProduto}?qtd=${qtdCopias}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}` 
            }
        });

        if(response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            window.open(url, '_blank');
            
            // Boa prática: limpa a URL temporária da memória após 5 segundos
            setTimeout(() => window.URL.revokeObjectURL(url), 5000);
        } else {
            // Captura o erro do backend (se houver) para facilitar o debug
            const erroTxt = await response.text();
            alert(`Erro ao gerar etiqueta: ${erroTxt || 'Acesso Negado / Falha no servidor'}`);
        }
    } catch(err) {
        console.error("Erro na requisição de etiquetas:", err);
        alert("Erro de conexão ao tentar gerar a etiqueta.");
    } finally {
        btnSubmit.innerHTML = textoOriginal;
    }
}

document.addEventListener('DOMContentLoaded', () => {
   const form = document.getElementById('formEtiqueta');
   if(form) form.addEventListener('submit', handleEtiquetaSubmit);
});