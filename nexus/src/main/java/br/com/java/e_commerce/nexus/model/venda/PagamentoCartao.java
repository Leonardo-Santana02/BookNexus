package br.com.java.e_commerce.nexus.model.venda;

import br.com.java.e_commerce.nexus.model.cliente.CartaoCredito;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "pagamento_cartoes")
public class PagamentoCartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @ManyToOne
    @JoinColumn(name = "cartao_id")
    private CartaoCredito cartaoCredito;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    private Integer parcelas = 1;

    // ===== GETTERS E SETTERS =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pagamento getPagamento() {
        return pagamento;
    }

    public void setPagamento(Pagamento pagamento) {
        this.pagamento = pagamento;
    }

    public CartaoCredito getCartaoCredito() {
        return cartaoCredito;
    }

    public void setCartaoCredito(CartaoCredito cartaoCredito) {
        this.cartaoCredito = cartaoCredito;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public void setValorPago(BigDecimal valorPago) {
        this.valorPago = valorPago;
    }

    public Integer getParcelas() {
        return parcelas;
    }

    public void setParcelas(Integer parcelas) {
        this.parcelas = parcelas;
    }
}