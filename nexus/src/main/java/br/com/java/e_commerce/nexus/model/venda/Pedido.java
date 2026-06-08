package br.com.java.e_commerce.nexus.model.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    @JsonIgnore  // IGNORA a serialização do cliente para evitar loop
    private Cliente cliente;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
<<<<<<< HEAD
    @JsonIgnore  // IGNORA a serialização dos itens na listagem principal
=======
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
    private List<ItemPedido> itens = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "endereco_entrega_id", nullable = false)
    @JsonIgnore  // IGNORA o endereço na serialização
    private Endereco enderecoEntrega;

    @Enumerated(EnumType.STRING)
    private StatusPedido status = StatusPedido.EM_ABERTO;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    private LocalDateTime dataConfirmacao;
    private LocalDateTime dataEnvio;
    private LocalDateTime dataEntrega;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal descontoPromocional = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorFrete = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(length = 500)
    private String resumoCuponsPromocionais;

    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL)
    @JsonIgnore  // IGNORA o pagamento na serialização
    private Pagamento pagamento;

    @Column(length = 50)
    private String codigoRastreio;

    // ===== CAMPO TRANSIENTE PARA CONTROLE DE SOLICITAÇÃO DE DEVOLUÇÃO PENDENTE =====
    // Este campo NÃO é persistido no banco de dados, serve apenas para uso em tempo de execução
    // na camada de apresentação (Thymeleaf) e lógica de negócio
    @Transient
    private Boolean temSolicitacaoPendente = false;

    // ===== MÉTODOS DE NEGÓCIO =====

    public BigDecimal calcularSubtotal() {
        if (itens == null || itens.isEmpty()) return BigDecimal.ZERO;
        return itens.stream()
                .map(ItemPedido::getPrecoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calcularTotal() {
        return subtotal
                .subtract(descontoPromocional)
                .add(valorFrete)
                .max(BigDecimal.ZERO);
    }

    public void confirmarPagamento() {
        this.status = StatusPedido.PAGO;
        this.dataConfirmacao = LocalDateTime.now();
    }

    public void enviar() {
        this.status = StatusPedido.ENVIADO;
        this.dataEnvio = LocalDateTime.now();
    }

    public void entregar() {
        this.status = StatusPedido.ENTREGUE;
        this.dataEntrega = LocalDateTime.now();
    }

    public void cancelar() {
        this.status = StatusPedido.CANCELADO;
    }

    @PrePersist
    @PreUpdate
    private void validarItens() {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalStateException("Pedido não pode ser salvo sem itens");
        }
    }

    public boolean temItens() {
        return itens != null && !itens.isEmpty();
    }

    public ItemPedido getPrimeiroItem() {
        if (!temItens()) return null;
        return itens.get(0);
    }

    public int getQuantidadeTotalItens() {
        if (!temItens()) return 0;
        return itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
    }

    // ===== GETTERS E SETTERS =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
<<<<<<< HEAD

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

=======
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) {
        if (itens == null) throw new IllegalArgumentException("Lista de itens não pode ser null");
        this.itens = itens;
        this.subtotal = calcularSubtotal();
        this.valorTotal = calcularTotal();
    }
<<<<<<< HEAD

    public Endereco getEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(Endereco enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataConfirmacao() { return dataConfirmacao; }
    public void setDataConfirmacao(LocalDateTime dataConfirmacao) { this.dataConfirmacao = dataConfirmacao; }

    public LocalDateTime getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDateTime dataEnvio) { this.dataEnvio = dataEnvio; }

    public LocalDateTime getDataEntrega() { return dataEntrega; }
    public void setDataEntrega(LocalDateTime dataEntrega) { this.dataEntrega = dataEntrega; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

=======
    public Endereco getEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(Endereco enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }
    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    public LocalDateTime getDataConfirmacao() { return dataConfirmacao; }
    public void setDataConfirmacao(LocalDateTime dataConfirmacao) { this.dataConfirmacao = dataConfirmacao; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDateTime dataEnvio) { this.dataEnvio = dataEnvio; }
    public LocalDateTime getDataEntrega() { return dataEntrega; }
    public void setDataEntrega(LocalDateTime dataEntrega) { this.dataEntrega = dataEntrega; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
    public BigDecimal getDescontoPromocional() { return descontoPromocional; }
    public void setDescontoPromocional(BigDecimal descontoPromocional) {
        this.descontoPromocional = descontoPromocional;
        this.valorTotal = calcularTotal();
    }
<<<<<<< HEAD

=======
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
    public BigDecimal getValorFrete() { return valorFrete; }
    public void setValorFrete(BigDecimal valorFrete) {
        this.valorFrete = valorFrete;
        this.valorTotal = calcularTotal();
    }
<<<<<<< HEAD

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public String getResumoCuponsPromocionais() { return resumoCuponsPromocionais; }
    public void setResumoCuponsPromocionais(String resumoCuponsPromocionais) { this.resumoCuponsPromocionais = resumoCuponsPromocionais; }

    public Pagamento getPagamento() { return pagamento; }
    public void setPagamento(Pagamento pagamento) { this.pagamento = pagamento; }

    public String getCodigoRastreio() { return codigoRastreio; }
    public void setCodigoRastreio(String codigoRastreio) { this.codigoRastreio = codigoRastreio; }

    // ===== GETTER E SETTER PARA O CAMPO TRANSIENTE =====
    // Este campo não é persistido no banco de dados, mas é usado pela view (Thymeleaf)

    @Transient
    public Boolean getTemSolicitacaoPendente() {
        return temSolicitacaoPendente != null && temSolicitacaoPendente;
    }

    public void setTemSolicitacaoPendente(Boolean temSolicitacaoPendente) {
        this.temSolicitacaoPendente = temSolicitacaoPendente;
    }
=======
    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }
    public String getResumoCuponsPromocionais() { return resumoCuponsPromocionais; }
    public void setResumoCuponsPromocionais(String resumoCuponsPromocionais) { this.resumoCuponsPromocionais = resumoCuponsPromocionais; }
    public Pagamento getPagamento() { return pagamento; }
    public void setPagamento(Pagamento pagamento) { this.pagamento = pagamento; }
    public String getCodigoRastreio() { return codigoRastreio; }
    public void setCodigoRastreio(String codigoRastreio) { this.codigoRastreio = codigoRastreio; }
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
}