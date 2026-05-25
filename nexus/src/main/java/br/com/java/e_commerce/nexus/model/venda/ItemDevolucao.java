package br.com.java.e_commerce.nexus.model.venda;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "itens_devolucao")
public class ItemDevolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "solicitacao_id")
    private SolicitacaoDevolucao solicitacao;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_pedido_id")
    private ItemPedido itemPedido;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorUnitarioDevolvido;

    @Column(length = 500)
    private String observacao;

    // Construtores
    public ItemDevolucao() {}

    public ItemDevolucao(ItemPedido itemPedido, Integer quantidade, BigDecimal valorUnitarioDevolvido) {
        this.itemPedido = itemPedido;
        this.quantidade = quantidade;
        this.valorUnitarioDevolvido = valorUnitarioDevolvido;
    }

    public BigDecimal getValorTotal() {
        return valorUnitarioDevolvido.multiply(BigDecimal.valueOf(quantidade));
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SolicitacaoDevolucao getSolicitacao() { return solicitacao; }
    public void setSolicitacao(SolicitacaoDevolucao solicitacao) { this.solicitacao = solicitacao; }

    public ItemPedido getItemPedido() { return itemPedido; }
    public void setItemPedido(ItemPedido itemPedido) { this.itemPedido = itemPedido; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public BigDecimal getValorUnitarioDevolvido() { return valorUnitarioDevolvido; }
    public void setValorUnitarioDevolvido(BigDecimal valorUnitarioDevolvido) { this.valorUnitarioDevolvido = valorUnitarioDevolvido; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}