package com.horus.projeto.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Transferência de saldo entre contas próprias. É NEUTRA ao DFC (não é receita/despesa):
 * vive fora do razão e afeta apenas os saldos por conta (− origem, + destino).
 */
@Entity
@Table(name = "transferencia")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferenciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cod_transferencia")
    private Long codTransferencia;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    @JsonIgnore
    private EmpresaEntity empresa;

    @Column(name = "cod_conta_origem", nullable = false)
    private Long codContaOrigem;

    @Column(name = "cod_conta_destino", nullable = false)
    private Long codContaDestino;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    @Column(length = 200)
    private String descricao;

    @Column(nullable = false)
    private Boolean estornado = false;

    @Column(name = "data_registro", updatable = false)
    private LocalDateTime dataRegistro;

    @PrePersist
    public void prePersist() {
        this.dataRegistro = LocalDateTime.now();
        if (this.estornado == null) this.estornado = false;
    }
}
