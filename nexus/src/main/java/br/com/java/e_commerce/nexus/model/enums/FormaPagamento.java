package br.com.java.e_commerce.nexus.model.enums;

public enum FormaPagamento {
    PIX("Pix"),
    BOLETO("Boleto"),
    CARTAO_CREDITO("Cartão de Crédito"),
    CARTAO_DEBITO("Cartão de Débito");

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}