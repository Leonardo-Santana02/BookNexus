package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.venda.Pagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    Optional<Pagamento> findByPedidoId(Long pedidoId);

    List<Pagamento> findByStatus(StatusPagamento status);

    @Query("SELECT p FROM Pagamento p WHERE p.pedido.cliente.id = :clienteId ORDER BY p.dataPagamento DESC")
    List<Pagamento> findByClienteId(@Param("clienteId") Long clienteId);

    boolean existsByPedidoId(Long pedidoId);
}