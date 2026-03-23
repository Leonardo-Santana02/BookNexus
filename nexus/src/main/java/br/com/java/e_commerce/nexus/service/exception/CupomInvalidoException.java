package br.com.java.e_commerce.nexus.service.exception;

public class CupomInvalidoException extends RuntimeException {

    public CupomInvalidoException(String message) {
        super(message);
    }

    public CupomInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}