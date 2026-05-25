package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.enums.StatusSolicitacao;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.venda.SolicitacaoDevolucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitacaoDevolucaoRepository extends JpaRepository<SolicitacaoDevolucao, Long> {

    // ===== MÉTODOS DERIVADOS =====

    /**
     * Busca todas as solicitações de um cliente, ordenadas por data decrescente
     */
    List<SolicitacaoDevolucao> findByClienteOrderByDataSolicitacaoDesc(Cliente cliente);

    /**
     * Busca todas as solicitações com um determinado status
     */
    List<SolicitacaoDevolucao> findByStatus(StatusSolicitacao status);

    /**
     * Busca a última solicitação de um pedido
     */
    Optional<SolicitacaoDevolucao> findFirstByPedidoOrderByDataSolicitacaoDesc(Pedido pedido);

    /**
     * Verifica se existe solicitação para um pedido com status dentro da lista informada
     */
    boolean existsByPedidoAndStatusIn(Pedido pedido, List<StatusSolicitacao> status);

    // ===== CONSULTAS JPQL =====

    /**
     * Busca solicitação por pedido ID e status específico
     */
    @Query("SELECT s FROM SolicitacaoDevolucao s WHERE s.pedido.id = :pedidoId AND s.status = :status")
    Optional<SolicitacaoDevolucao> findByPedidoIdAndStatus(@Param("pedidoId") Long pedidoId,
                                                           @Param("status") StatusSolicitacao status);

    /**
     * Busca solicitações em um período específico
     */
    @Query("SELECT s FROM SolicitacaoDevolucao s WHERE s.dataSolicitacao BETWEEN :inicio AND :fim")
    List<SolicitacaoDevolucao> findSolicitacoesPorPeriodo(@Param("inicio") LocalDateTime inicio,
                                                          @Param("fim") LocalDateTime fim);

    /**
     * Conta quantas solicitações existem com um determinado status
     */
    long countByStatus(StatusSolicitacao status);

    /**
     * Verifica se um item do pedido já foi devolvido em alguma solicitação
     * (ignora solicitações canceladas ou recusadas)
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SolicitacaoDevolucao s " +
            "JOIN s.itensDevolvidos i " +
            "WHERE i.itemPedido.id = :itemPedidoId " +
            "AND s.status NOT IN ('CANCELADA', 'RECUSADA')")
    boolean existsItemDevolvido(@Param("itemPedidoId") Long itemPedidoId);

    /**
     * Busca todas as solicitações com itens carregados (evita N+1)
     */
    @Query("SELECT DISTINCT s FROM SolicitacaoDevolucao s " +
            "LEFT JOIN FETCH s.itensDevolvidos i " +
            "LEFT JOIN FETCH i.itemPedido ip " +
            "LEFT JOIN FETCH ip.produto " +
            "WHERE s.cliente.id = :clienteId " +
            "ORDER BY s.dataSolicitacao DESC")
    List<SolicitacaoDevolucao> findByClienteIdWithItens(@Param("clienteId") Long clienteId);

    /**
     * Busca todas as solicitações com itens carregados (para admin)
     */
    @Query("SELECT DISTINCT s FROM SolicitacaoDevolucao s " +
            "LEFT JOIN FETCH s.itensDevolvidos i " +
            "LEFT JOIN FETCH i.itemPedido ip " +
            "LEFT JOIN FETCH ip.produto " +
            "ORDER BY s.dataSolicitacao DESC")
    List<SolicitacaoDevolucao> findAllWithItens();

    /**
     * Busca solicitações por status com itens carregados
     */
    @Query("SELECT DISTINCT s FROM SolicitacaoDevolucao s " +
            "LEFT JOIN FETCH s.itensDevolvidos i " +
            "LEFT JOIN FETCH i.itemPedido ip " +
            "LEFT JOIN FETCH ip.produto " +
            "WHERE s.status = :status " +
            "ORDER BY s.dataSolicitacao DESC")
    List<SolicitacaoDevolucao> findByStatusWithItens(@Param("status") StatusSolicitacao status);

    /**
     * Busca solicitações pendentes (para notificações admin)
     */
    @Query("SELECT s FROM SolicitacaoDevolucao s WHERE s.status = 'PENDENTE' ORDER BY s.dataSolicitacao ASC")
    List<SolicitacaoDevolucao> findPendentesOrderByData();

    /**
     * Busca solicitações aprovadas aguardando recebimento
     */
    @Query("SELECT s FROM SolicitacaoDevolucao s WHERE s.status = 'APROVADA' ORDER BY s.dataAprovacao ASC")
    List<SolicitacaoDevolucao> findAprovadasAguardandoRecebimento();

    /**
     * Conta solicitações por status e mês
     */
    @Query("SELECT FUNCTION('MONTH', s.dataSolicitacao), FUNCTION('YEAR', s.dataSolicitacao), COUNT(s) " +
            "FROM SolicitacaoDevolucao s " +
            "WHERE s.status = :status " +
            "GROUP BY FUNCTION('YEAR', s.dataSolicitacao), FUNCTION('MONTH', s.dataSolicitacao) " +
            "ORDER BY FUNCTION('YEAR', s.dataSolicitacao) DESC, FUNCTION('MONTH', s.dataSolicitacao) DESC")
    List<Object[]> countByStatusAndMonth(@Param("status") StatusSolicitacao status);

    /**
     * Busca solicitação por código do cupom gerado
     */
    @Query("SELECT s FROM SolicitacaoDevolucao s WHERE s.novoPedidoId IS NOT NULL " +
            "AND s.novoPedidoId = :pedidoId")
    Optional<SolicitacaoDevolucao> findByNovoPedidoId(@Param("pedidoId") Long pedidoId);

    /**
     * Verifica se o cliente tem alguma solicitação pendente
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SolicitacaoDevolucao s " +
            "WHERE s.cliente.id = :clienteId " +
            "AND s.status IN ('PENDENTE', 'APROVADA', 'AGUARDANDO_RECEBIMENTO')")
    boolean existsSolicitacaoPendenteByClienteId(@Param("clienteId") Long clienteId);
}