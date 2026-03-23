package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByClienteOrderByDataCriacaoDesc(Cliente cliente);

    List<Pedido> findByStatus(StatusPedido status);

    @Query("SELECT p FROM Pedido p WHERE p.dataCriacao BETWEEN :inicio AND :fim " +
            "AND (p.status = 'PAGO' OR p.status = 'ENTREGUE')")
    List<Pedido> findVendasPorPeriodo(@Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim);

    @Query("SELECT p FROM Pedido p WHERE p.cliente.id = :clienteId " +
            "ORDER BY p.dataCriacao DESC")
    List<Pedido> findByClienteId(@Param("clienteId") Long clienteId);

    @Query("SELECT COUNT(p) FROM Pedido p WHERE p.cliente.id = :clienteId " +
            "AND p.status = 'ENTREGUE'")
    long countPedidosEntreguesByClienteId(@Param("clienteId") Long clienteId);

    @Query("SELECT COALESCE(SUM(p.valorTotal), 0) FROM Pedido p " +
            "WHERE p.cliente.id = :clienteId AND p.status = 'ENTREGUE'")
    double somaTotalGastoByClienteId(@Param("clienteId") Long clienteId);
}