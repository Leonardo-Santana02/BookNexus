package br.com.java.e_commerce.nexus.model.cliente;

import br.com.java.e_commerce.nexus.model.enums.Genero;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clientes")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 14) // CPF formatado ###.###.###-##
    private String cpf;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Genero genero;

    @Column(nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataNascimento;

    @Column(nullable = false)
    private Boolean inativado = false;

    // Relacionamento com Endereços
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Endereco> enderecos = new ArrayList<>();

    // Relacionamento com Telefones
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Telefone> telefones = new ArrayList<>();

    // Relacionamento com Cartões de Crédito
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<CartaoCredito> cartoesCredito = new ArrayList<>();

    // Relacionamento com Pedidos (ADICIONADO)
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Pedido> pedidos = new ArrayList<>();

    public Cliente() {}

    // Inclui todos os campos obrigatórios
    public Cliente(String nome, String cpf, String email, String senha, Genero genero, LocalDate dataNascimento) {
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.senha = senha;
        this.genero = genero;
        this.dataNascimento = dataNascimento;
        this.inativado = false; // valor padrão
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public Genero getGenero() { return genero; }
    public void setGenero(Genero genero) { this.genero = genero; }

    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }

    public Boolean getInativado() { return inativado; }
    public void setInativado(Boolean inativado) { this.inativado = inativado; }

    public List<Endereco> getEnderecos() { return enderecos; }
    public void setEnderecos(List<Endereco> enderecos) { this.enderecos = enderecos; }

    public List<Telefone> getTelefones() { return telefones; }
    public void setTelefones(List<Telefone> telefones) { this.telefones = telefones; }

    public List<CartaoCredito> getCartoesCredito() { return cartoesCredito; }
    public void setCartoesCredito(List<CartaoCredito> cartoesCredito) { this.cartoesCredito = cartoesCredito; }

    public List<Pedido> getPedidos() { return pedidos; }
    public void setPedidos(List<Pedido> pedidos) { this.pedidos = pedidos; }

    // Métodos para gerenciar os relacionamentos bidirecionais
    public void addEndereco(Endereco endereco) {
        enderecos.add(endereco);
        endereco.setCliente(this);
    }

    public void removeEndereco(Endereco endereco) {
        enderecos.remove(endereco);
        endereco.setCliente(null);
    }

    public void addTelefone(Telefone telefone) {
        telefones.add(telefone);
        telefone.setCliente(this);
    }

    public void removeTelefone(Telefone telefone) {
        telefones.remove(telefone);
        telefone.setCliente(null);
    }

    public void addCartaoCredito(CartaoCredito cartao) {
        cartoesCredito.add(cartao);
        cartao.setCliente(this);
    }

    public void removeCartaoCredito(CartaoCredito cartao) {
        cartoesCredito.remove(cartao);
        cartao.setCliente(null);
    }

    public void addPedido(Pedido pedido) {
        pedidos.add(pedido);
        pedido.setCliente(this);
    }

    public void removePedido(Pedido pedido) {
        pedidos.remove(pedido);
        pedido.setCliente(null);
    }

    // Método para exclusão lógica
    public void inativar() {
        this.inativado = true;
    }

    public void ativar() {
        this.inativado = false;
    }

    @Override
    public String toString() {
        return "Cliente{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", email='" + email + '\'' +
                ", inativado=" + inativado +
                '}';
    }
}