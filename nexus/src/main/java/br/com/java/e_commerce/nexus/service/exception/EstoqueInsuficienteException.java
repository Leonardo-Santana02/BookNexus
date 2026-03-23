package br.com.java.e_commerce.nexus.service.exception;

public class EstoqueInsuficienteException extends RuntimeException {

    public EstoqueInsuficienteException(String message) {
        super(message);
    }

    public EstoqueInsuficienteException(String message, Throwable cause) {
        super(message, cause);
    }
}