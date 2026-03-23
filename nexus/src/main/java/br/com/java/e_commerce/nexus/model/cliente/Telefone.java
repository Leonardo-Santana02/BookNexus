package br.com.java.e_commerce.nexus.model.cliente;

import br.com.java.e_commerce.nexus.model.enums.TipoTelefone;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "telefones")
public class Telefone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2)
    private String ddd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTelefone tipo;

    @Column(nullable = false, length = 15) // Formato 00000-0000
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    public Telefone() {}

    public Telefone(String ddd, String numero, Cliente cliente) {
        this.ddd = ddd;
        this.numero = numero;
        this.cliente = cliente;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TipoTelefone getTipo() { return tipo; }
    public void setTipo(TipoTelefone tipo) { this.tipo = tipo; }

    public String getDdd() { return ddd; }
    public void setDdd(String ddd) { this.ddd = ddd; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    @Override
    public String toString() {
        String tipoStr = (tipo != null) ? tipo.name() : "";
        String dddStr = (ddd != null) ? ddd : "";
        String numeroStr = (numero != null) ? numero : "";
        return dddStr + " - " + numeroStr + " - " + tipoStr;
    }


}