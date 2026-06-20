package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conta_pagar")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"itens", "parcelas", "empresa"})
public class ContaPagarEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_conta_pagar")
    private Long codContaPagar;

    @Column(name = "descricao", nullable = false, length = 200)
    private String descricao;

    @Column(name = "fornecedor", length = 150)
    private String fornecedor;

    @Column(name = "numero_nota_fiscal", length = 50)
    private String numeroNotaFiscal;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    /**
     * Data de vencimento principal da conta.
     * Preenchida automaticamente como a maior data de vencimento entre as parcelas.
     */
    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    /**
     * Flag que marca a conta como integralmente paga.
     * Quando true, o status sempre retorna PAGA independente das parcelas.
     */
    @Column(nullable = false)
    private Boolean paga = false;

    @Column(name = "data_registro", nullable = false, updatable = false)
    private LocalDateTime dataRegistro;

    /** Classe financeira ANALÍTICA (CUSTO/DESPESA) do título inteiro. */
    @Column(name = "cod_classe")
    private Long codClasse;

    /** Conta financeira de onde o pagamento sai (informada na baixa). */
    @Column(name = "cod_conta_financeira")
    private Long codContaFinanceira;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    @OneToMany(mappedBy = "contaPagar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ContaPagarItemEntity> itens = new ArrayList<>();

    @OneToMany(mappedBy = "contaPagar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ContaPagarParcelaEntity> parcelas = new ArrayList<>();

    /**
     * Status calculado dinamicamente — não persiste no banco.
     * Regras: PAGA > VENCIDA (alguma parcela vencida não paga) > EM_ABERTO
     */
    public String getStatus() {
        if (Boolean.TRUE.equals(paga)) return "PAGA";
        LocalDate hoje = LocalDate.now();
        if (parcelas != null && !parcelas.isEmpty()) {
            boolean algumVencida = parcelas.stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getPaga()))
                    .anyMatch(p -> p.getDataVencimento() != null && p.getDataVencimento().isBefore(hoje));
            if (algumVencida) return "VENCIDA";
        } else if (dataVencimento != null && dataVencimento.isBefore(hoje)) {
            return "VENCIDA";
        }
        return "EM_ABERTO";
    }

    @PrePersist
    public void prePersist() {
        this.dataRegistro = LocalDateTime.now();
        if (this.paga == null) this.paga = false;
        if (this.valorTotal == null) this.valorTotal = BigDecimal.ZERO;
        if (this.itens == null) this.itens = new ArrayList<>();
        if (this.parcelas == null) this.parcelas = new ArrayList<>();
    }
}
