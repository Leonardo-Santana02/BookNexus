package br.com.java.e_commerce.nexus.model.enums;

public enum MotivoDevolucao {
    ARREPENDIMENTO("Arrependimento/Desistência", 7),
    DEFEITO_FABRICACAO("Defeito de fabricação", 30),
    TAMANHO_ERRADO("Tamanho errado", 30),
    COR_DIFERENTE("Cor diferente do esperado", 30),
    PRODUTO_DANIFICADO("Produto danificado no transporte", 7),
    ENTREGA_ATRASADA("Entrega fora do prazo", 7),
    NAO_QUIS_MAIS("Não quis mais o produto", 7),
    OUTRO("Outro motivo", 7);

    private final String descricao;
    private final int prazoDias; // dias a partir da entrega para solicitar

    MotivoDevolucao(String descricao, int prazoDias) {
        this.descricao = descricao;
        this.prazoDias = prazoDias;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getPrazoDias() {
        return prazoDias;
    }
}