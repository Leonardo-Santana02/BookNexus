package br.com.java.e_commerce.nexus.repository.carrinho;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repositório para operações de acesso a dados da entidade Carrinho.
 *
 * Esta interface estende JpaRepository, que fornece métodos CRUD básicos:
 * - save(), findById(), findAll(), delete(), etc.
 *
 * @author E-Commerce Nexus Team
 * @see JpaRepository
 * @see Carrinho
 */
public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    // ===== MÉTODOS DERIVADOS (QUERY BY METHOD NAME) =====

    /**
     * Busca o carrinho de um cliente pelo seu ID.
     *
     * Este método utiliza o recurso "Query by Method Name" do Spring Data JPA.
     * O Spring analisa o nome do método e gera a consulta automaticamente:
     *
     * "findBy" + "ClienteId" → WHERE cliente.id = :clienteId
     *
     * @param clienteId ID do cliente dono do carrinho
     * @return Optional contendo o Carrinho se encontrado, ou Optional.empty() caso contrário
     *
     * @example
     * Optional<Carrinho> carrinho = repository.findByClienteId(1L);
     * if (carrinho.isPresent()) {
     *     // Carrinho encontrado
     * }
     */
    Optional<Carrinho> findByClienteId(Long clienteId);

    // ===== MÉTODOS COM QUERIES JPQL PERSONALIZADAS =====

    /**
     * Busca o carrinho de um cliente com seus itens carregados antecipadamente (fetch).
     *
     * ⚠️ PROBLEMA QUE ESTE MÉTODO RESOLVE: N+1 Queries
     *
     * Sem o FETCH, ao acessar carrinho.getItens() fora da transação,
     * o Hibernate faria uma consulta adicional para cada carrinho (problema N+1).
     *
     * Com LEFT JOIN FETCH, todos os dados são carregados em uma ÚNICA consulta,
     * melhorando significativamente a performance.
     *
     * @param clienteId ID do cliente dono do carrinho
     * @return Optional contendo o Carrinho com itens carregados, ou Optional.empty() se não encontrado
     *
     * @example
     * // Em uma transação (ex: no CarrinhoService)
     * Carrinho carrinho = repository.buscarComItensPorClienteId(clienteId)
     *     .orElseThrow(() -> new RuntimeException("Carrinho não encontrado"));
     *
     * // Os itens já estão carregados - NÃO causa consulta adicional!
     * for (ItemCarrinho item : carrinho.getItens()) {
     *     System.out.println(item.getProduto().getTitulo());
     * }
     */
    @Query("SELECT c FROM Carrinho c LEFT JOIN FETCH c.itens WHERE c.cliente.id = :clienteId")
    Optional<Carrinho> buscarComItensPorClienteId(@Param("clienteId") Long clienteId);

    // ===== MÉTODOS DE DELETE PERSONALIZADOS =====

    /**
     * Remove o carrinho de um cliente pelo seu ID.
     *
     * Este método gera automaticamente um DELETE baseado no nome:
     * "deleteBy" + "ClienteId" → DELETE FROM Carrinho WHERE cliente.id = :clienteId
     *
     * ⚠️ IMPORTANTE: Este método NÃO carrega a entidade antes de deletar.
     * É mais eficiente que usar findById() + delete() pois executa
     * uma única operação DELETE diretamente no banco.
     *
     * @param clienteId ID do cliente cujo carrinho será removido
     *
     * @example
     * // Quando o cliente faz logout ou solicita exclusão de conta
     * repository.deleteByClienteId(clienteId);
     *
     * // Isso executa algo como:
     * // DELETE FROM carrinho WHERE cliente_id = 1
     */
    void deleteByClienteId(Long clienteId);
}