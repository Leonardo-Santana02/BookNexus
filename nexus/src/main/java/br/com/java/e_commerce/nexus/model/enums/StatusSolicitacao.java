package br.com.java.e_commerce.nexus.model.enums;

public enum StatusSolicitacao {
    PENDENTE("Aguardando análise"),
    APROVADA("Aprovada - Aguardando envio do produto"),
    RECUSADA("Recusada"),
    AGUARDANDO_RECEBIMENTO("Aguardando recebimento do produto"),
    RECEBIDA("Produto recebido pela loja"),
    TROCA_AUTORIZADA("Troca autorizada"),
    CONCLUIDA("Devolução concluída"),
    CANCELADA("Cancelada pelo cliente");

    private final String descricao;

    StatusSolicitacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}