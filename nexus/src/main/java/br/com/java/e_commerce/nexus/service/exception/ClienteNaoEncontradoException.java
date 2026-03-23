package br.com.java.e_commerce.nexus.service.exception;

public class ClienteNaoEncontradoException extends RuntimeException {
    public ClienteNaoEncontradoException(Long id) {
        super("Cliente não encontrado com ID: " + id);
    }

    public ClienteNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}