package br.com.java.e_commerce.nexus.service.exception;

public class CartaoNaoEncontradoException extends RuntimeException {
    public CartaoNaoEncontradoException(Long id) {
        super("Cartão de crédito não encontrado com ID: " + id);
    }

    public CartaoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}