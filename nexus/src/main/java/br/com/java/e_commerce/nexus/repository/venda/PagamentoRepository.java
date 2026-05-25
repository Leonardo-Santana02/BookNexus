package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.venda.Pagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório para operações de acesso a dados da entidade Pagamento.
 *
 * Esta interface gerencia os pagamentos realizados no sistema,
 * permitindo buscar pagamentos por pedido, cliente, status, etc.
 *
 * Relacionamentos importantes:
 * - Um Pagamento pertence a um Pedido (relacionamento OneToOne)
 * - Um Pagamento pode ter vários PagamentoCartao (relacionamento OneToMany)
 * - Um Pagamento pode ter vários Cupons (relacionamento ManyToMany)
 *
 * @see JpaRepository
 * @see Pagamento
 * @see StatusPagamento
 */
@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    // ===== MÉTODOS DERIVADOS (QUERY BY METHOD NAME) =====

    /**
     * Busca o pagamento associado a um pedido específico.
     *
     * Como a relação entre Pedido e Pagamento é OneToOne,
     * cada pedido tem NO MÁXIMO um pagamento.
     *
     * @param pedidoId ID do pedido
     * @return Optional com o pagamento se existir, ou Optional.empty() caso contrário
     *
     * @example
     * Optional<Pagamento> pagamento = pagamentoRepository.findByPedidoId(pedidoId);
     * if (pagamento.isPresent()) {
     *     System.out.println("Status do pagamento: " + pagamento.get().getStatus());
     * } else {
     *     System.out.println("Pedido ainda não foi pago");
     * }
     */
    Optional<Pagamento> findByPedidoId(Long pedidoId);

    /**
     * Busca todos os pagamentos com um determinado status.
     *
     * @param status Status do pagamento (PENDENTE, APROVADO, REJEITADO)
     * @return Lista de pagamentos com o status especificado
     *
     * @example
     * // Para dashboard administrativo
     * List<Pagamento> pendentes = pagamentoRepository.findByStatus(StatusPagamento.PENDENTE);
     * List<Pagamento> aprovados = pagamentoRepository.findByStatus(StatusPagamento.APROVADO);
     * List<Pagamento> rejeitados = pagamentoRepository.findByStatus(StatusPagamento.REJEITADO);
     *
     * System.out.println("Pagamentos pendentes: " + pendentes.size());
     * System.out.println("Pagamentos aprovados: " + aprovados.size());
     * System.out.println("Pagamentos rejeitados: " + rejeitados.size());
     */
    List<Pagamento> findByStatus(StatusPagamento status);

    // ===== CONSULTAS JPQL PERSONALIZADAS =====

    /**
     * Busca todos os pagamentos de um cliente, ordenados por data decrescente
     * (do mais recente para o mais antigo).
     *
     * Esta consulta navega pelo relacionamento:
     * Pagamento → Pedido → Cliente
     *
     * @param clienteId ID do cliente
     * @return Lista de pagamentos do cliente (ordenados do mais recente para o mais antigo)
     *
     * @example
     * // No ClienteService ou PagamentoService
     * List<Pagamento> historicoPagamentos = pagamentoRepository.findByClienteId(1L);
     *
     * for (Pagamento pagamento : historicoPagamentos) {
     *     System.out.println("Pedido #" + pagamento.getPedido().getId() +
     *                        " - Valor: R$ " + pagamento.getValor() +
     *                        " - Status: " + pagamento.getStatus());
     * }
     */
    @Query("SELECT p FROM Pagamento p WHERE p.pedido.cliente.id = :clienteId ORDER BY p.dataPagamento DESC")
    List<Pagamento> findByClienteId(@Param("clienteId") Long clienteId);

    /**
     * Verifica se existe algum pagamento associado a um pedido.
     *
     * ⚡ MÉTODO OTIMIZADO - Retorna apenas um booleano, sem carregar a entidade completa!
     *
     * Utilizado principalmente para:
     * - Evitar duplicidade de pagamento no mesmo pedido
     * - Validar se pedido já foi pago antes de permitir novo pagamento
     *
     * @param pedidoId ID do pedido
     * @return true se existe pagamento para o pedido, false caso contrário
     *
     * @example
     * // No PagamentoService.processarPagamento()
     * if (pagamentoRepository.existsByPedidoId(pedidoId)) {
     *     throw new RuntimeException("Este pedido já possui um pagamento registrado");
     * }
     *
     * // SQL gerado: SELECT COUNT(*) > 0 FROM pagamentos WHERE pedido_id = ?
     * // Muito mais eficiente que carregar a entidade completa!
     */
    boolean existsByPedidoId(Long pedidoId);
}