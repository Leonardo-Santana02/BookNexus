package br.com.java.e_commerce.nexus.model.enums;

public enum StatusPedido {
    EM_ABERTO("Aguardando pagamento"),
    AGUARDANDO_PAGAMENTO("Aguardando confirmação"),
    PAGO("Pagamento confirmado"),
    EM_SEPARACAO("Em separação"),
    ENVIADO("Enviado para entrega"),
    ENTREGUE("Entregue"),
    CANCELADO("Cancelado"),
    AGUARDANDO_DEVOLUCAO("Aguardando devolução"),
    DEVOLUCAO_CONFIRMADA("Devolução confirmada"),
    DEVOLVIDO("Devolvido");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}