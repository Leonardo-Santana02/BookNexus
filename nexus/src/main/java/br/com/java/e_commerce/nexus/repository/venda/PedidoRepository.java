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

/**
 * Repositório para operações de acesso a dados da entidade Pedido.
 *
 * Esta interface estende JpaRepository, fornecendo métodos CRUD básicos
 * e permitindo a definição de consultas personalizadas.
 *
 * @Repository: Anotação opcional para repositórios Spring Data JPA
 * (Spring já detecta automaticamente interfaces que extendem JpaRepository)
 *
 * @see JpaRepository
 * @see Pedido
 */
@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // ===== MÉTODOS DERIVADOS (QUERY BY METHOD NAME) =====

    /**
     * Busca todos os pedidos de um cliente, ordenados por data de criação decrescente
     * (do mais recente para o mais antigo).
     *
     * O Spring Data JPA gera a consulta automaticamente baseada no nome do método:
     * "findBy" + "Cliente" + "OrderBy" + "DataCriacao" + "Desc"
     *
     * SQL gerado:
     * SELECT * FROM pedidos WHERE cliente_id = ? ORDER BY data_criacao DESC
     *
     * @param cliente Objeto Cliente (não o ID)
     * @return Lista de pedidos do cliente (vazia se nenhum encontrado)
     *
     * @example
     * Cliente cliente = clienteService.buscarPorId(1L);
     * List<Pedido> pedidos = pedidoRepository.findByClienteOrderByDataCriacaoDesc(cliente);
     */
    List<Pedido> findByClienteOrderByDataCriacaoDesc(Cliente cliente);

    /**
     * Busca todos os pedidos com um determinado status.
     *
     * @param status Status do pedido (EM_ABERTO, PAGO, ENVIADO, ENTREGUE, etc.)
     * @return Lista de pedidos com o status especificado
     *
     * @example
     * List<Pedido> pedidosPagos = pedidoRepository.findByStatus(StatusPedido.PAGO);
     * List<Pedido> pedidosAguardando = pedidoRepository.findByStatus(StatusPedido.AGUARDANDO_DEVOLUCAO);
     */
    List<Pedido> findByStatus(StatusPedido status);

    // ===== CONSULTAS JPQL PERSONALIZADAS =====

    /**
     * Busca vendas realizadas em um período específico.
     *
     * Considera apenas pedidos com status PAGO ou ENTREGUE (vendas efetivadas).
     * Pedidos EM_ABERTO, CANCELADOS ou com problemas não são incluídos.
     *
     * @param inicio Data/hora inicial do período (inclusiva)
     * @param fim Data/hora final do período (inclusiva)
     * @return Lista de pedidos que representam vendas no período
     *
     * @example
     * LocalDateTime inicio = LocalDateTime.now().minusMonths(1);
     * LocalDateTime fim = LocalDateTime.now();
     * List<Pedido> vendasMes = pedidoRepository.findVendasPorPeriodo(inicio, fim);
     */
    @Query("SELECT p FROM Pedido p WHERE p.dataCriacao BETWEEN :inicio AND :fim " +
            "AND (p.status = 'PAGO' OR p.status = 'ENTREGUE')")
    List<Pedido> findVendasPorPeriodo(@Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim);

    /**
     * Busca todos os pedidos de um cliente pelo seu ID.
     *
     * Utiliza JPQL ao invés de query derivada para demonstrar sintaxe.
     * Ordena do mais recente para o mais antigo.
     *
     * @param clienteId ID do cliente
     * @return Lista de pedidos do cliente
     *
     * @example
     * List<Pedido> pedidos = pedidoRepository.findByClienteId(1L);
     */
    @Query("SELECT p FROM Pedido p WHERE p.cliente.id = :clienteId " +
            "ORDER BY p.dataCriacao DESC")
    List<Pedido> findByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Busca todos os pedidos de um cliente com seus itens e produtos carregados.
     *
     * ⚠️ SOLUÇÃO PARA O PROBLEMA N+1 DE CONSULTAS!
     *
     * Este método utiliza JOIN FETCH para carregar antecipadamente:
     * - Os itens do pedido (ItemPedido)
     * - Os produtos associados a cada item
     *
     * SEM este método, acessar pedido.getItens() causaria uma consulta adicional
     * para cada pedido (N+1 queries).
     *
     * COM este método, tudo é carregado em UMA ÚNICA consulta.
     *
     * @param clienteId ID do cliente
     * @return Lista de pedidos com itens e produtos carregados
     *
     * @example
     * // Em uma transação (ex: PedidoService)
     * List<Pedido> pedidos = pedidoRepository.findByClienteIdWithItens(clienteId);
     *
     * // Os itens e produtos já estão carregados - NÃO causa consultas adicionais!
     * for (Pedido pedido : pedidos) {
     *     for (ItemPedido item : pedido.getItens()) {
     *         System.out.println(item.getProduto().getTitulo());
     *     }
     * }
     */
    @Query("SELECT DISTINCT p FROM Pedido p " +
            "LEFT JOIN FETCH p.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "WHERE p.cliente.id = :clienteId " +
            "ORDER BY p.dataCriacao DESC")
    List<Pedido> findByClienteIdWithItens(@Param("clienteId") Long clienteId);

    /**
     * Conta quantos pedidos entregues um cliente possui.
     *
     * Utiliza COUNT no banco de dados, mais eficiente que contar em memória.
     *
     * @param clienteId ID do cliente
     * @return Número de pedidos entregues pelo cliente
     *
     * @example
     * long pedidosEntregues = pedidoRepository.countPedidosEntreguesByClienteId(1L);
     * // Útil para calcular nível de fidelidade do cliente
     */
    @Query("SELECT COUNT(p) FROM Pedido p WHERE p.cliente.id = :clienteId " +
            "AND p.status = 'ENTREGUE'")
    long countPedidosEntreguesByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Calcula o valor total gasto por um cliente em pedidos entregues.
     *
     * Utiliza COALESCE para retornar 0 (zero) quando o cliente não tem pedidos,
     * evitando retornar null.
     *
     * @param clienteId ID do cliente
     * @return Valor total gasto pelo cliente (double)
     *
     * @example
     * double totalGasto = pedidoRepository.somaTotalGastoByClienteId(1L);
     * // Útil para:
     * // - Calcular cashback
     * // - Definir categorias de cliente (bronze, prata, ouro)
     * // - Oferecer frete grátis para clientes VIP
     */
    @Query("SELECT COALESCE(SUM(p.valorTotal), 0) FROM Pedido p " +
            "WHERE p.cliente.id = :clienteId AND p.status = 'ENTREGUE'")
    double somaTotalGastoByClienteId(@Param("clienteId") Long clienteId);
}