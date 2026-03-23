package br.com.java.e_commerce.nexus.model.carrinho;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.produto.Produto;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "carrinhos")
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToMany(mappedBy = "carrinho", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCarrinho> itens = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "carrinho_cupons",
            joinColumns = @JoinColumn(name = "carrinho_id"),
            inverseJoinColumns = @JoinColumn(name = "cupom_id")
    )
    private List<Cupom> cupons = new ArrayList<>();

    private BigDecimal valorFrete = BigDecimal.ZERO;
    private String tipoFreteSelecionado;

    // ===== MÉTODOS DE NEGÓCIO =====

    public void adicionarItem(Produto produto, int quantidade) {
        Optional<ItemCarrinho> itemExistente = itens.stream()
                .filter(item -> item.getProduto().getId().equals(produto.getId()))
                .findFirst();

        if (itemExistente.isPresent()) {
            ItemCarrinho item = itemExistente.get();
            item.setQuantidade(item.getQuantidade() + quantidade);
        } else {
            ItemCarrinho item = new ItemCarrinho();
            item.setProduto(produto);
            item.setQuantidade(quantidade);
            item.setCarrinho(this);
            itens.add(item);
        }
    }

    public void alterarQuantidadeItem(Long produtoId, int novaQuantidade) {
        Optional<ItemCarrinho> item = itens.stream()
                .filter(i -> i.getProduto().getId().equals(produtoId))
                .findFirst();

        if (item.isPresent()) {
            if (novaQuantidade <= 0) {
                itens.remove(item.get());
            } else {
                item.get().setQuantidade(novaQuantidade);
            }
        }
    }

    public void removerItem(Long produtoId) {
        itens.removeIf(item -> item.getProduto().getId().equals(produtoId));
    }

    public BigDecimal getSubtotal() {
        return itens.stream()
                .map(ItemCarrinho::getPrecoFinal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getDescontoTotal() {
        return cupons.stream()
                .filter(Cupom::isValido)
                .map(cupom -> cupom.calcularDesconto(getSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotal() {
        BigDecimal subtotal = getSubtotal();
        BigDecimal desconto = getDescontoTotal();
        BigDecimal total = subtotal.subtract(desconto).add(valorFrete);
        return total.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public void adicionarCupom(Cupom cupom) {
        if (cupom.isValido() && !cupons.contains(cupom)) {
            cupons.add(cupom);
        }
    }

    public void removerCupom(Cupom cupom) {
        cupons.remove(cupom);
    }

    public boolean isEmpty() {
        return itens.isEmpty();
    }

    public int getQuantidadeItens() {
        return itens.stream().mapToInt(ItemCarrinho::getQuantidade).sum();
    }

    // ===== GETTERS E SETTERS =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public List<ItemCarrinho> getItens() {
        return itens;
    }

    public void setItens(List<ItemCarrinho> itens) {
        this.itens = itens;
    }

    public List<Cupom> getCupons() {
        return cupons;
    }

    public void setCupons(List<Cupom> cupons) {
        this.cupons = cupons;
    }

    public BigDecimal getValorFrete() {
        return valorFrete;
    }

    public void setValorFrete(BigDecimal valorFrete) {
        this.valorFrete = valorFrete;
    }

    public String getTipoFreteSelecionado() {
        return tipoFreteSelecionado;
    }

    public void setTipoFreteSelecionado(String tipoFreteSelecionado) {
        this.tipoFreteSelecionado = tipoFreteSelecionado;
    }
}