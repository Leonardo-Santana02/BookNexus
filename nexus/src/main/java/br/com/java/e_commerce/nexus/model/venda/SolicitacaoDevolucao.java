package br.com.java.e_commerce.nexus.model.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.enums.MotivoDevolucao;
import br.com.java.e_commerce.nexus.model.enums.StatusSolicitacao;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "solicitacoes_devolucao")
public class SolicitacaoDevolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorSolicitado;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorAprovado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSolicitacao status = StatusSolicitacao.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoDevolucao motivo;

    @Column(length = 500)
    private String justificativa;

    @Column(length = 1000)
    private String observacaoAdmin;

    @Column(nullable = false)
    private LocalDateTime dataSolicitacao = LocalDateTime.now();

    private LocalDateTime dataAprovacao;
    private LocalDateTime dataRecebimento;
    private LocalDateTime dataConclusao;

    @OneToMany(mappedBy = "solicitacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemDevolucao> itensDevolvidos = new ArrayList<>();

    private Long novoPedidoId; // ID do pedido gerado em caso de troca

    // ===== MÉTODOS DE NEGÓCIO =====

    public void aprovar(String observacao) {
        this.status = StatusSolicitacao.APROVADA;
        this.observacaoAdmin = observacao;
        this.dataAprovacao = LocalDateTime.now();
    }

    public void aprovar(BigDecimal valorAprovado, String observacao) {
        this.valorAprovado = valorAprovado;
        this.status = StatusSolicitacao.APROVADA;
        this.observacaoAdmin = observacao;
        this.dataAprovacao = LocalDateTime.now();
    }

    public void recusar(String motivoRecusa) {
        this.status = StatusSolicitacao.RECUSADA;
        this.observacaoAdmin = motivoRecusa;
        this.dataConclusao = LocalDateTime.now();
    }

    public void confirmarRecebimento() {
        this.status = StatusSolicitacao.RECEBIDA;
        this.dataRecebimento = LocalDateTime.now();
    }

    public void concluir() {
        this.status = StatusSolicitacao.CONCLUIDA;
        this.dataConclusao = LocalDateTime.now();
    }

    public void cancelar() {
        this.status = StatusSolicitacao.CANCELADA;
        this.dataConclusao = LocalDateTime.now();
    }

    public BigDecimal calcularValorTotalItens() {
        return itensDevolvidos.stream()
                .map(ItemDevolucao::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isDevolucaoParcial() {
        if (pedido == null || pedido.getItens() == null) return false;
        return itensDevolvidos.size() < pedido.getItens().size();
    }

    // ===== GETTERS E SETTERS =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public BigDecimal getValorSolicitado() {
        return valorSolicitado;
    }

    public void setValorSolicitado(BigDecimal valorSolicitado) {
        this.valorSolicitado = valorSolicitado;
    }

    public BigDecimal getValorAprovado() {
        return valorAprovado;
    }

    public void setValorAprovado(BigDecimal valorAprovado) {
        this.valorAprovado = valorAprovado;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }

    public void setStatus(StatusSolicitacao status) {
        this.status = status;
    }

    public MotivoDevolucao getMotivo() {
        return motivo;
    }

    public void setMotivo(MotivoDevolucao motivo) {
        this.motivo = motivo;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }

    public String getObservacaoAdmin() {
        return observacaoAdmin;
    }

    public void setObservacaoAdmin(String observacaoAdmin) {
        this.observacaoAdmin = observacaoAdmin;
    };

    public LocalDateTime getDataSolicitacao() {
        return dataSolicitacao;
    }

    public void setDataSolicitacao(LocalDateTime dataSolicitacao) {
        this.dataSolicitacao = dataSolicitacao;
    }

    public LocalDateTime getDataAprovacao() {
        return dataAprovacao;
    }

    public void setDataAprovacao(LocalDateTime dataAprovacao) {
        this.dataAprovacao = dataAprovacao;
    }

    public LocalDateTime getDataRecebimento() {
        return dataRecebimento;
    }

    public void setDataRecebimento(LocalDateTime dataRecebimento) {
        this.dataRecebimento = dataRecebimento;
    }

    public LocalDateTime getDataConclusao() {
        return dataConclusao;
    }

    public void setDataConclusao(LocalDateTime dataConclusao) {
        this.dataConclusao = dataConclusao;
    }

    public List<ItemDevolucao> getItensDevolvidos() {
        return itensDevolvidos;
    }

    public void setItensDevolvidos(List<ItemDevolucao> itensDevolvidos) {
        this.itensDevolvidos = itensDevolvidos;
    }

    public Long getNovoPedidoId() {
        return novoPedidoId;
    }

    public void setNovoPedidoId(Long novoPedidoId) {
        this.novoPedidoId = novoPedidoId;
    }
}