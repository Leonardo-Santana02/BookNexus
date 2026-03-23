package br.com.java.e_commerce.nexus.model.enums;
public enum TipoLogradouro {
    RUA("Rua"),
    AVENIDA("Avenida"),
    ALAMEDA("Alameda"),
    TRAVESSA("Travessa"),
    RODOVIA("Rodovia"),
    ESTRADA("Estrada"),
    PRACA("Praça"),
    VIELA("Viela");

    private final String descricao;

    TipoLogradouro(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}