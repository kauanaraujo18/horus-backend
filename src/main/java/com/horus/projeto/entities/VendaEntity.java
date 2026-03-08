package com.horus.projeto.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "venda")
@Data // Gera Getters, Setters, toString, equals e hashcode
@NoArgsConstructor
@AllArgsConstructor
public class VendaEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_venda")
    private Long codVenda;

    @Column(name = "data_registro", nullable = false, updatable = false)
    private LocalDateTime dataRegistro;

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "desconto")
    private BigDecimal desconto;

    @Column(name = "acrescimo")
    private BigDecimal acrescimo;

    @Column(name = "qtd_parcelas")
    private Integer qtdParcelas;

    @Column(name = "valor_pago")
    private BigDecimal valorPago;

    @Column(name = "troco")
    private BigDecimal troco;

    @Column(name = "valor_dinheiro")
    private BigDecimal valorDinheiro;

    @Column(name = "valor_pix")
    private BigDecimal valorPix;

    @Column(name = "valor_credito")
    private BigDecimal valorCredito;

    @Column(name = "valor_debito")
    private BigDecimal valorDebito;

    // Relacionamento Um-para-Muitos com o Item
    // cascade = ALL: Salvar Venda salva Itens. Deletar Venda deleta Itens.
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProdutoVendaEntity> itens = new ArrayList<>();

    @ManyToOne // Muitas vendas podem ser de Um usuário
    @JoinColumn(name = "usuario_id") // Nome da coluna no banco
    private UsuarioEntity usuario;

    public UsuarioEntity getUsuario() { return usuario; }
    public void setUsuario(UsuarioEntity usuario) { this.usuario = usuario; }

    // --- Métodos Auxiliares (Hooks do JPA) ---

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    @PrePersist
    public void prePersist() {
        this.dataRegistro = LocalDateTime.now();
        if (this.dataVenda == null) {
            this.dataVenda = LocalDateTime.now();
        }
        if (this.desconto == null) this.desconto = BigDecimal.ZERO;
        if (this.acrescimo == null) this.acrescimo = BigDecimal.ZERO;
        
        // Garante que a lista de itens não seja nula
        if (this.itens == null) this.itens = new ArrayList<>();
    }
}