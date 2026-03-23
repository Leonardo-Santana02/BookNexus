package br.com.java.e_commerce.nexus.model.cliente;

import br.com.java.e_commerce.nexus.model.enums.TipoLogradouro;
import br.com.java.e_commerce.nexus.model.enums.UF;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "enderecos")
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoLogradouro tipoLogradouro;

    @Column(nullable = false, length = 100)
    private String rua;

    @Column(nullable = false)
    private String numero;

    @Column(length = 50)
    private String complemento;

    @Column(nullable = false, length = 50)
    private String bairro;

    @Column(nullable = false, length = 50)
    private String cidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private UF uf;

    @Column(nullable = false, length = 9) // CEP formatado 00000-000
    private String cep;

    @Column(length = 255)
    private String observacoes;

    @Column(length = 50)
    private String apelido;

    @Column(nullable = false)
    private boolean enderecoEntrega = false;

    @Column(nullable = false)
    private boolean enderecoCobranca = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    public Endereco() {}

    // Sugestão de construtor completo
    public Endereco(TipoLogradouro tipoLogradouro, String rua, String numero,
                    String complemento, String bairro, String cidade, UF uf,
                    String cep, String observacoes, String apelido,
                    boolean enderecoCobranca, boolean enderecoEntrega, Cliente cliente) {
        this.tipoLogradouro = tipoLogradouro;
        this.rua = rua;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.cep = cep;
        this.observacoes = observacoes;
        this.apelido = apelido;
        this.enderecoEntrega = enderecoEntrega;
        this.enderecoCobranca = enderecoCobranca;
        this.cliente = cliente;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRua() { return rua; }
    public void setRua(String rua) { this.rua = rua; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public UF getUf() { return uf; }
    public void setUf(UF uf) { this.uf = uf; }

    public TipoLogradouro getTipoLogradouro() { return tipoLogradouro; }
    public void setTipoLogradouro(TipoLogradouro tipoLogradouro) { this.tipoLogradouro = tipoLogradouro; }

    public String getApelido() { return apelido; }
    public void setApelido(String apelido) { this.apelido = apelido; }

    public boolean isEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(boolean enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }

    public boolean isEnderecoCobranca() { return enderecoCobranca; }
    public void setEnderecoCobranca(boolean enderecoCobranca) { this.enderecoCobranca = enderecoCobranca; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    @Override
    public String toString() {
        String tipoLogradouroStr = (tipoLogradouro != null) ? tipoLogradouro.getDescricao() : "";
        String ruaStr = (rua != null) ? rua : "";
        String numeroStr = (numero != null) ? numero : "";
        String bairroStr = (bairro != null) ? bairro : "";
        String cidadeStr = (cidade != null) ? cidade : "";
        String ufStr = (uf != null) ? uf.getSigla() : "";
        String cepStr = (cep != null) ? cep : "";

        return tipoLogradouroStr + " " + ruaStr + ", " + numeroStr + " - " +
                bairroStr + ", " + cidadeStr + " - " + ufStr + " | CEP: " + cepStr;
    }
}