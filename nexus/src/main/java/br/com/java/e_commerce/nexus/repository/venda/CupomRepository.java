package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório para operações de acesso a dados da entidade Cupom.
 *
 * Esta interface gerencia cupons promocionais e de troca, fornecendo
 * métodos para buscar, validar e gerenciar cupons ativos.
 *
 * Características importantes:
 * - Cupons podem ser GLOBAIS (cliente = null) ou ESPECÍFICOS (cliente != null)
 * - Cupons têm validade (dataValidade) e limite de usos (maximoUsos)
 * - Cupons podem ser desativados manualmente (ativo = false)
 *
 * @see JpaRepository
 * @see Cupom
 */
@Repository
public interface CupomRepository extends JpaRepository<Cupom, Long> {

    // ===== MÉTODOS DERIVADOS (QUERY BY METHOD NAME) =====

    /**
     * Busca um cupom pelo código, considerando apenas cupons ativos.
     *
     * @param codigo Código do cupom (ex: "BLACKFRIDAY", "WELCOME10")
     * @return Optional com o cupom se encontrado e ativo
     *
     * @example
     * Optional<Cupom> cupom = cupomRepository.findByCodigoAndAtivoTrue("BLACKFRIDAY");
     * if (cupom.isPresent()) {
     *     // Cupom encontrado e está ativo
     * }
     */
    Optional<Cupom> findByCodigoAndAtivoTrue(String codigo);

    /**
     * Busca todos os cupons ativos de um cliente, ordenados por data de validade decrescente
     * (do que expira mais tarde para o que expira mais cedo).
     *
     * @param cliente Objeto Cliente (não o ID)
     * @return Lista de cupons ativos do cliente
     *
     * @example
     * Cliente cliente = clienteService.buscarPorId(1L);
     * List<Cupom> meusCupons = cupomRepository.findByClienteAndAtivoTrueOrderByDataValidadeDesc(cliente);
     */
    List<Cupom> findByClienteAndAtivoTrueOrderByDataValidadeDesc(Cliente cliente);

    /**
     * Busca todos os cupons ativos de um determinado tipo, ordenados por data de validade decrescente.
     *
     * @param tipo Tipo do cupom (PROMOCIONAL ou TROCA)
     * @return Lista de cupons ativos do tipo especificado
     *
     * @example
     * // Buscar todos os cupons promocionais ativos
     * List<Cupom> promocionais = cupomRepository.findByTipoAndAtivoTrueOrderByDataValidadeDesc(TipoCupom.PROMOCIONAL);
     *
     * // Buscar todos os cupons de troca ativos
     * List<Cupom> trocas = cupomRepository.findByTipoAndAtivoTrueOrderByDataValidadeDesc(TipoCupom.TROCA);
     */
    List<Cupom> findByTipoAndAtivoTrueOrderByDataValidadeDesc(TipoCupom tipo);

    // ===== CONSULTAS JPQL PERSONALIZADAS =====

    /**
     * Busca todos os cupons válidos para um cliente.
     *
     * ⭐ MÉTODO MAIS IMPORTANTE PARA O CARRINHO!
     *
     * Considera um cupom como VÁLIDO quando:
     * 1. Está ativo (ativo = true)
     * 2. Não está expirado (dataValidade > data atual)
     * 3. É global (cliente IS NULL) OU pertence ao cliente (cliente = :cliente)
     *
     * IMPORTANTE: Este método NÃO verifica o limite de usos (maximoUsos).
     * Essa verificação deve ser feita no serviço ou na entidade.
     *
     * @param cliente Objeto Cliente (para verificar cupons específicos)
     * @return Lista de cupons válidos para o cliente (globais + seus cupons)
     *
     * @example
     * Cliente cliente = clienteService.buscarPorId(1L);
     * List<Cupom> cuponsValidos = cupomRepository.findCuponsValidosParaCliente(cliente);
     *
     * // Resultado inclui:
     * // - Cupons globais como "BLACKFRIDAY" (cliente = null)
     * // - Cupons específicos do cliente como "TROCA-ABC123"
     */
    @Query("SELECT c FROM Cupom c WHERE c.ativo = true AND c.dataValidade > CURRENT_TIMESTAMP " +
            "AND (c.cliente IS NULL OR c.cliente = :cliente)")
    List<Cupom> findCuponsValidosParaCliente(@Param("cliente") Cliente cliente);

    /**
     * Busca cupons ativos de um cliente por tipo específico.
     *
     * @param cliente Objeto Cliente
     * @param tipo Tipo do cupom (PROMOCIONAL ou TROCA)
     * @return Lista de cupons ativos do cliente para o tipo especificado
     *
     * @example
     * Cliente cliente = clienteService.buscarPorId(1L);
     *
     * // Buscar apenas cupons de troca do cliente
     * List<Cupom> cuponsTroca = cupomRepository.findByClienteAndTipoAndAtivoTrue(cliente, TipoCupom.TROCA);
     *
     * // Buscar apenas cupons promocionais do cliente
     * List<Cupom> cuponsPromocionais = cupomRepository.findByClienteAndTipoAndAtivoTrue(cliente, TipoCupom.PROMOCIONAL);
     */
    List<Cupom> findByClienteAndTipoAndAtivoTrue(Cliente cliente, TipoCupom tipo);

    /**
     * Conta quantos cupons ativos de um determinado tipo um cliente possui.
     *
     * @param cliente Objeto Cliente
     * @param tipo Tipo do cupom
     * @return Quantidade de cupons ativos do cliente para o tipo especificado
     *
     * @example
     * Cliente cliente = clienteService.buscarPorId(1L);
     * long qtdTrocas = cupomRepository.countByClienteAndTipoAndAtivoTrue(cliente, TipoCupom.TROCA);
     *
     * if (qtdTrocas > 0) {
     *     System.out.println("Cliente tem " + qtdTrocas + " cupons de troca disponíveis");
     * }
     */
    long countByClienteAndTipoAndAtivoTrue(Cliente cliente, TipoCupom tipo);

    /**
     * Remove todos os cupons de um cliente.
     *
     * ⚠️ OPERAÇÃO PERIGOSA - Use com cautela!
     *
     * Útil para:
     * - Exclusão de conta de cliente (GDPR)
     * - Limpeza de dados em ambiente de teste
     * - Migração de dados
     *
     * @param cliente Objeto Cliente cujos cupons serão removidos
     *
     * @example
     * Cliente cliente = clienteService.buscarPorId(1L);
     * cupomRepository.deleteByCliente(cliente);
     * // Todos os cupons do cliente serão deletados permanentemente!
     */
    void deleteByCliente(Cliente cliente);

    /**
     * Busca um cupom válido pelo código para um cliente específico.
     *
     * ⭐ MÉTODO USADO NA VALIDAÇÃO DE CUPOM DURANTE O CHECKOUT!
     *
     * Valida todas as condições em uma ÚNICA consulta:
     * 1. Cupom existe com o código informado
     * 2. Cupom está ativo (ativo = true)
     * 3. Cupom não está expirado (dataValidade > agora)
     * 4. Cupom é global (cliente IS NULL) OU pertence ao cliente
     *
     * NOTA: Este método NÃO verifica o limite de usos (maximoUsos).
     *
     * @param codigo Código do cupom informado pelo cliente
     * @param clienteId ID do cliente que está tentando usar o cupom
     * @return Optional com o cupom se todas as validações passarem
     *
     * @example
     * // No CupomService.validarCupom()
     * Optional<Cupom> cupom = cupomRepository.findValidoPorCodigoECliente("BLACKFRIDAY", 1L);
     *
     * if (cupom.isEmpty()) {
     *     throw new CupomInvalidoException("Cupom inválido, expirado ou não disponível");
     * }
     */
    @Query("SELECT c FROM Cupom c WHERE c.codigo = :codigo AND c.ativo = true " +
            "AND c.dataValidade > CURRENT_TIMESTAMP " +
            "AND (c.cliente IS NULL OR c.cliente.id = :clienteId)")
    Optional<Cupom> findValidoPorCodigoECliente(@Param("codigo") String codigo,
                                                @Param("clienteId") Long clienteId);
}