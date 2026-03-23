package br.com.java.e_commerce.nexus.model.enums;

public enum TipoCupom {
    PROMOCIONAL("Cupom Promocional - aplicado no carrinho"),
    TROCA("Cupom de Troca - aplicado no pagamento");

    private final String descricao;

    TipoCupom(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}