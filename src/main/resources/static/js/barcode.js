

/**
 * Lógica de Geração do PDF
 */
async function handleRelatorioSubmit(e) {
    e.preventDefault(); 
    const qtd = document.getElementById('relatorioQtd').value;
    const pos = document.getElementById('relatorioPosicao').value;
    const btnSubmit = e.target.querySelector('button[type="submit"]');
    
    // UI Loading
    const textoOriginal = btnSubmit.innerHTML;
    btnSubmit.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Gerando...';
    btnSubmit.disabled = true;

    try {
        const response = await fetch(`${API_URL}/relatorios/codigo-barras`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ quantidade: parseInt(qtd), posicao: parseInt(pos) })
        });

        if (response.ok) {
            const blob = await response.blob();
            const urlArquivo = window.URL.createObjectURL(blob);
            window.open(urlArquivo, '_blank');
            setTimeout(() => window.URL.revokeObjectURL(urlArquivo), 1000);
            showMessage("Relatório gerado com sucesso!");
        } else {
            showMessage("Erro ao gerar PDF.", true);
        }
    } catch (error) {
        console.error(error);
        showMessage("Erro de conexão.", true);
    } finally {
        btnSubmit.innerHTML = textoOriginal;
        btnSubmit.disabled = false;
    }
}

/* =================================================================================
   6. UTILITÁRIOS E FORMATAÇÕES
   ================================================================================= */

document.addEventListener('DOMContentLoaded', () => {
    // Máscara de Moeda (R$)
    const inputValor = document.getElementById('produtoValor');
    if (inputValor) {
        inputValor.addEventListener('input', (e) => {
            let value = e.target.value.replace(/\D/g, "");
            if (value === "") { e.target.value = ""; return; }
            const floatValue = parseFloat(value) / 100;
            e.target.value = floatValue.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
        });
    }

    // Gerador EAN-13 no click
    const inputCodigo = document.getElementById('produtoCodigo');
    if (inputCodigo) {
        inputCodigo.addEventListener('click', function() {
            if (this.hasAttribute('readonly')) {
                // Se estiver readonly, libera para edição manual
                this.removeAttribute('readonly');
                this.focus();
            } else if (!this.value) {
                // Se estiver vazio e editável (ou se preferir gerar ao clicar), gera
                this.value = gerarEAN13();
            }
        });
        
        // Bloqueia novamente ao sair
        inputCodigo.addEventListener('blur', function() {
            this.setAttribute('readonly', 'true');
        });
    }
});

/**
 * Gera código EAN-13 válido
 */
function gerarEAN13() {
    let codigo = "789"; 
    while (codigo.length < 12) {
        codigo += Math.floor(Math.random() * 10);
    }
    let soma = 0;
    for (let i = 0; i < 12; i++) {
        let digito = parseInt(codigo[i]);
        if (i % 2 === 0) soma += digito * 1;
        else soma += digito * 3;
    }
    let resto = soma % 10;
    let digitoVerificador = (resto === 0) ? 0 : (10 - resto);
    return codigo + digitoVerificador;
}