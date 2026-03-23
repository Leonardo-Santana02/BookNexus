package br.com.java.e_commerce.nexus.repository.carrinho;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    Optional<Carrinho> findByClienteId(Long clienteId);

    @Query("SELECT c FROM Carrinho c LEFT JOIN FETCH c.itens WHERE c.cliente.id = :clienteId")
    Optional<Carrinho> buscarComItensPorClienteId(@Param("clienteId") Long clienteId);

    void deleteByClienteId(Long clienteId);
}