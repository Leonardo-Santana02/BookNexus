package br.com.java.e_commerce.nexus.model.enums;

public enum BandeiraCartao {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    ELO("Elo"),
    AMERICAN_EXPRESS("American Express");

    private String descricao;

    BandeiraCartao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
