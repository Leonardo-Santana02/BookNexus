package br.com.java.e_commerce.nexus.model.carrinho;

import br.com.java.e_commerce.nexus.model.produto.Produto;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "itens_carrinho")
public class ItemCarrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private int quantidade;

    @ManyToOne
    @JoinColumn(name = "carrinho_id")
    private Carrinho carrinho;

    // ===== MÉTODOS =====
    public BigDecimal getPrecoFinal() {
        return produto.getPreco()
                .multiply(BigDecimal.valueOf(quantidade));
    }

    // ===== GETTERS E SETTERS =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public Carrinho getCarrinho() {
        return carrinho;
    }

    public void setCarrinho(Carrinho carrinho) {
        this.carrinho = carrinho;
    }
}