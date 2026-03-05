USE Josys;

-- **INÍCIO VERSÃO 1.0.0**

-- 2. Tabela de Produtos
CREATE TABLE produto (
    cod_produto BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(255) NOT NULL UNIQUE, -- Código de Barras
    nome VARCHAR(255) NOT NULL,
    valor DECIMAL(19, 2) NOT NULL
);

-- 3. Tabela de Vendas (Cabeçalho)
CREATE TABLE venda (
    cod_venda BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_registro DATETIME NOT NULL,
    data_venda DATETIME NOT NULL,
    forma_pagamento VARCHAR(50),
    qtd_parcelas INT,
    desconto DECIMAL(19, 2) DEFAULT 0.00,
    acrescimo DECIMAL(19, 2) DEFAULT 0.00,
    valor_total DECIMAL(19, 2) NOT NULL
);

-- 4. Tabela de Itens da Venda (Relacionamento)
CREATE TABLE produto_venda (
    cod_item_venda BIGINT AUTO_INCREMENT PRIMARY KEY,
    cod_venda BIGINT NOT NULL,
    cod_produto BIGINT NOT NULL,
    quantidade INT NOT NULL,
    valor_unitario DECIMAL(19, 2) NOT NULL,
    valor_total_item DECIMAL(19, 2) NOT NULL,
    
    -- Chaves Estrangeiras
    CONSTRAINT fk_venda FOREIGN KEY (cod_venda) REFERENCES venda(cod_venda),
    CONSTRAINT fk_produto FOREIGN KEY (cod_produto) REFERENCES produto(cod_produto)
);

-- **FIM VERSÃO 1.0.0**


-- **INÍCIO VERSÃO 1.0.1**

ALTER TABLE venda
ADD valor_pago DECIMAL(19, 2) DEFAULT 0.00,
    troco DECIMAL(19, 2) DEFAULT 0.00;


-- 1. Remove a coluna antiga (se der erro de constraint, avise, mas geralmente vai direto)
ALTER TABLE venda DROP COLUMN forma_pagamento;

-- 2. Adiciona as colunas discriminadas
ALTER TABLE venda ADD 
	valor_dinheiro DECIMAL(19, 2) DEFAULT 0.00,
	valor_pix DECIMAL(19, 2) DEFAULT 0.00,
	valor_credito DECIMAL(19, 2) DEFAULT 0.00,
	valor_debito DECIMAL(19, 2) DEFAULT 0.00;

-- **FIM VERSÃO 1.0.1**

