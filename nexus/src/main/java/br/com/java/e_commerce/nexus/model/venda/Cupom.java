package br.com.java.e_commerce.nexus.model.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "cupons")
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCupom tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(nullable = false)
    private LocalDateTime dataValidade;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    private Integer quantidadeUsos = 0;
    private Integer maximoUsos;

    // ===== CONSTRUTORES =====
    public Cupom() {}

    public Cupom(String codigo, TipoCupom tipo, BigDecimal valor, LocalDateTime dataValidade) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.valor = valor;
        this.dataValidade = dataValidade;
    }

    // ===== MÉTODOS DE NEGÓCIO =====

    public boolean isValido() {
        return ativo &&
                dataValidade.isAfter(LocalDateTime.now()) &&
                (maximoUsos == null || quantidadeUsos < maximoUsos);
    }

    public BigDecimal calcularDesconto(BigDecimal valorCompra) {
        if (!isValido()) {
            return BigDecimal.ZERO;
        }

        // Para cupons de troca, o valor é fixo
        if (tipo == TipoCupom.TROCA) {
            return valor.min(valorCompra).setScale(2, RoundingMode.HALF_UP);
        }

        // Para cupons promocionais, o valor é percentual ou fixo?
        // Assumindo que seja fixo por enquanto
        return valor.min(valorCompra).setScale(2, RoundingMode.HALF_UP);
    }

    public void consumir() {
        if (!isValido()) {
            throw new IllegalStateException("Cupom não está válido para consumo");
        }
        this.quantidadeUsos++;
        if (maximoUsos != null && quantidadeUsos >= maximoUsos) {
            this.ativo = false;
        }
    }

    // ===== GETTERS E SETTERS =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public TipoCupom getTipo() {
        return tipo;
    }

    public void setTipo(TipoCupom tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDateTime getDataValidade() {
        return dataValidade;
    }

    public void setDataValidade(LocalDateTime dataValidade) {
        this.dataValidade = dataValidade;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Integer getQuantidadeUsos() {
        return quantidadeUsos;
    }

    public void setQuantidadeUsos(Integer quantidadeUsos) {
        this.quantidadeUsos = quantidadeUsos;
    }

    public Integer getMaximoUsos() {
        return maximoUsos;
    }

    public void setMaximoUsos(Integer maximoUsos) {
        this.maximoUsos = maximoUsos;
    }
}