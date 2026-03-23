package br.com.java.e_commerce.nexus.model.venda;

import br.com.java.e_commerce.nexus.model.cliente.CartaoCredito;
import br.com.java.e_commerce.nexus.model.enums.FormaPagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPagamento;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status = StatusPagamento.PENDENTE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(precision = 10, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime dataPagamento;

    @Column(length = 500)
    private String motivoRejeicao;

    @Column(length = 500)
    private String resumoCupons;

    @OneToOne
    @JoinColumn(name = "pedido_id", unique = true)
    private Pedido pedido;

    @OneToMany(mappedBy = "pagamento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PagamentoCartao> cartoesUsados = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "pagamento_cupons",
            joinColumns = @JoinColumn(name = "pagamento_id"),
            inverseJoinColumns = @JoinColumn(name = "cupom_id")
    )
    private List<Cupom> cuponsUsados = new ArrayList<>();

    // ===== MÉTODOS DE NEGÓCIO =====

    public void adicionarCartao(CartaoCredito cartao, BigDecimal valorPago, int parcelas) {
        PagamentoCartao pagamentoCartao = new PagamentoCartao();
        pagamentoCartao.setPagamento(this);
        pagamentoCartao.setCartaoCredito(cartao);
        pagamentoCartao.setValorPago(valorPago);
        pagamentoCartao.setParcelas(parcelas);
        cartoesUsados.add(pagamentoCartao);
    }

    public BigDecimal getValorTotalCartoes() {
        return cartoesUsados.stream()
                .map(PagamentoCartao::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calcularDescontoTotal() {
        return cuponsUsados.stream()
                .filter(Cupom::isValido)
                .map(Cupom::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getResumoCartoes() {
        if (cartoesUsados.isEmpty()) return null;
        return cartoesUsados.stream()
                .map(pc -> "Cartão final " + pc.getCartaoCredito().getNumeroCartao().substring(pc.getCartaoCredito().getNumeroCartao().length() - 4) +
                        ": R$ " + pc.getValorPago() +
                        (pc.getParcelas() > 1 ? " (" + pc.getParcelas() + "x)" : ""))
                .collect(Collectors.joining(", "));
    }

    public void aprovar() {
        this.status = StatusPagamento.APROVADO;
        this.motivoRejeicao = null;
    }

    public void rejeitar(String motivo) {
        this.status = StatusPagamento.REJEITADO;
        this.motivoRejeicao = motivo;
    }

    // ===== GETTERS E SETTERS =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public void setStatus(StatusPagamento status) {
        this.status = status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public void setDesconto(BigDecimal desconto) {
        this.desconto = desconto;
    }

    public LocalDateTime getDataPagamento() {
        return dataPagamento;
    }

    public void setDataPagamento(LocalDateTime dataPagamento) {
        this.dataPagamento = dataPagamento;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public void setMotivoRejeicao(String motivoRejeicao) {
        this.motivoRejeicao = motivoRejeicao;
    }

    public String getResumoCupons() {
        return resumoCupons;
    }

    public void setResumoCupons(String resumoCupons) {
        this.resumoCupons = resumoCupons;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public List<PagamentoCartao> getCartoesUsados() {
        return cartoesUsados;
    }

    public void setCartoesUsados(List<PagamentoCartao> cartoesUsados) {
        this.cartoesUsados = cartoesUsados;
    }

    public List<Cupom> getCuponsUsados() {
        return cuponsUsados;
    }

    public void setCuponsUsados(List<Cupom> cuponsUsados) {
        this.cuponsUsados = cuponsUsados;
    }
}