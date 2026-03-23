package br.com.java.e_commerce.nexus.model.cliente;

import br.com.java.e_commerce.nexus.model.enums.BandeiraCartao;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "cartoes_credito")
public class CartaoCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numeroCartao;

    @Column(nullable = false)
    private String nomeImpresso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BandeiraCartao bandeira;

    @Column(nullable = false, length = 3)
    private String codigoSeguranca;

    @Column(nullable = false)
    private boolean preferencial = false;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroCartao() {
        return numeroCartao;
    }

    public void setNumeroCartao(String numeroCartao) {
        this.numeroCartao = numeroCartao;
    }

    public String getNomeImpresso() {
        return nomeImpresso;
    }

    public void setNomeImpresso(String nomeImpresso) {
        this.nomeImpresso = nomeImpresso;
    }

    public BandeiraCartao getBandeira() {
        return bandeira;
    }

    public void setBandeira(BandeiraCartao bandeira) {
        this.bandeira = bandeira;
    }

    public String getCodigoSeguranca() {
        return codigoSeguranca;
    }

    public void setCodigoSeguranca(String codigoSeguranca) {
        this.codigoSeguranca = codigoSeguranca;
    }

    public boolean isPreferencial() {
        return preferencial;
    }

    public void setPreferencial(boolean preferencial) {
        this.preferencial = preferencial;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }
}
