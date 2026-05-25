package br.com.java.e_commerce.nexus.service.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.venda.CupomRepository;
import br.com.java.e_commerce.nexus.service.exception.CupomInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerenciar todas as operações relacionadas a cupons
 * no sistema de e-commerce. Inclui criação, validação, consulta e consumo de cupons.
 *
 * A anotação @Service indica que esta classe é um componente Spring do tipo serviço,
 * permitindo injeção de dependência e gerenciamento pelo container Spring.
 *
 * A anotação @Transactional no nível da classe garante que todos os métodos públicos
 * serão executados dentro de uma transação de banco de dados, mantendo a consistência
 * dos dados e permitindo rollback automático em caso de exceções não tratadas.
 */
@Service
@Transactional
public class CupomService {

    // Repositório para operações de persistência da entidade Cupom
    private final CupomRepository cupomRepository;

    // Repositório para buscar informações do cliente associado ao cupom
    private final ClienteRepository clienteRepository;

    /**
     * Construtor para injeção de dependências (Spring irá fornecer as implementações).
     * Por ser o único construtor, o Spring automaticamente o utiliza para injeção,
     * sem necessidade da anotação @Autowired explícita.
     *
     * @param cupomRepository Implementação Spring Data JPA do repositório de cupons
     * @param clienteRepository Implementação Spring Data JPA do repositório de clientes
     */
    public CupomService(CupomRepository cupomRepository, ClienteRepository clienteRepository) {
        this.cupomRepository = cupomRepository;
        this.clienteRepository = clienteRepository;
    }

    // ===== CRIAÇÃO DE CUPONS =====

    /**
     * Cria um cupom promocional geral (disponível para qualquer cliente).
     *
     * @param codigo Código único do cupom que o cliente irá digitar (ex: "BLACKFRIDAY")
     * @param valor Valor do desconto (pode ser valor fixo ou percentual, dependendo da regra de negócio)
     * @param dataValidade Data e hora em que o cupom expira
     * @param descricao Descrição textual do cupom para exibição ao cliente
     * @return Cupom persistido no banco de dados com seu ID gerado
     */
    public Cupom criarCupomPromocional(String codigo, BigDecimal valor,
                                       LocalDateTime dataValidade, String descricao) {
        // Instancia um novo objeto Cupom (entidade JPA)
        Cupom cupom = new Cupom();

        // Converte o código para maiúsculas para padronização e evitar duplicidade case-sensitive
        cupom.setCodigo(codigo.toUpperCase());

        // Define o tipo como PROMOCIONAL (diferente de TROCA, por exemplo)
        cupom.setTipo(TipoCupom.PROMOCIONAL);

        // Define o valor do desconto (montante fixo ou percentual, conforme modelo)
        cupom.setValor(valor);

        // Define até quando o cupom pode ser utilizado
        cupom.setDataValidade(dataValidade);

        // Define a descrição amigável para exibição no front-end
        cupom.setDescricao(descricao);

        // Define um limite máximo de 100 utilizações para este cupom promocional geral
        cupom.setMaximoUsos(100);

        // Persiste o cupom no banco de dados e retorna a entidade com o ID gerado
        return cupomRepository.save(cupom);
    }

    /**
     * Cria um cupom promocional exclusivo para um cliente específico.
     * Gera automaticamente um código único baseado no valor do cupom e um UUID.
     *
     * @param clienteId ID do cliente que receberá este cupom personalizado
     * @param valor Valor do desconto para este cupom
     * @param descricao Descrição do cupom
     * @return Cupom persistido e vinculado ao cliente específico
     * @throws RuntimeException Se o cliente não for encontrado no banco de dados
     */
    public Cupom criarCupomPromocionalParaCliente(Long clienteId, BigDecimal valor, String descricao) {
        // Busca o cliente pelo ID; lança exceção se não existir
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // Gera um código único para o cupom personalizado
        // Exemplo: "PROMO-50.00-AB3F2D" onde AB3F2D é parte aleatória de um UUID
        String codigo = "PROMO-" + valor.stripTrailingZeros().toPlainString()
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        cupom.setTipo(TipoCupom.PROMOCIONAL);
        cupom.setValor(valor);
        cupom.setCliente(cliente); // Vincula o cupom a um cliente específico
        cupom.setDataValidade(LocalDateTime.now().plusMonths(3)); // Válido por 3 meses a partir de agora
        cupom.setDescricao(descricao);
        cupom.setMaximoUsos(1); // Uso único para cupons personalizados

        return cupomRepository.save(cupom);
    }

    /**
     * Cria um cupom do tipo TROCA, geralmente usado para reembolsos ou créditos
     * concedidos ao cliente por algum motivo (devolução, erro, etc).
     *
     * @param clienteId ID do cliente que receberá o crédito de troca
     * @param valor Valor do crédito a ser concedido
     * @param descricao Descrição do motivo da troca
     * @return Cupom de troca persistido
     * @throws RuntimeException Se o cliente não for encontrado
     */
    public Cupom criarCupomTroca(Long clienteId, BigDecimal valor, String descricao) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // Gera código com prefixo TROCA e parte aleatória de UUID
        String codigo = "TROCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        cupom.setTipo(TipoCupom.TROCA);
        cupom.setValor(valor);
        cupom.setCliente(cliente);
        cupom.setDataValidade(LocalDateTime.now().plusMonths(6)); // Cupom de troca tem validade maior (6 meses)
        cupom.setDescricao(descricao);
        cupom.setMaximoUsos(1); // Uso único

        return cupomRepository.save(cupom);
    }

    // ===== VALIDAÇÃO E CONSULTA =====

    /**
     * Valida se um cupom pode ser utilizado por um determinado cliente.
     * Verifica se o cupom existe, está ativo, não expirou, não atingiu o limite de usos,
     * e está disponível para o cliente (se é global ou específico).
     *
     * @param codigo Código do cupom informado pelo cliente
     * @param clienteId ID do cliente que está tentando usar o cupom
     * @return Cupom válido e pronto para uso
     * @throws CupomInvalidoException Se o cupom não for válido para este cliente
     */
    public Cupom validarCupom(String codigo, Long clienteId) {
        // Utiliza query customizada do repositório que já aplica todas as regras de validação
        Cupom cupom = cupomRepository.findValidoPorCodigoECliente(codigo, clienteId)
                .orElseThrow(() -> new CupomInvalidoException(
                        "Cupom inválido, expirado ou não disponível para este cliente"));

        return cupom;
    }

    /**
     * Busca um cupom pelo código, considerando apenas cupons ativos.
     *
     * @param codigo Código do cupom a ser buscado
     * @return Optional contendo o cupom se encontrado e ativo, ou vazio caso contrário
     */
    public Optional<Cupom> buscarPorCodigo(String codigo) {
        return cupomRepository.findByCodigoAndAtivoTrue(codigo);
    }

    /**
     * Busca todos os cupons válidos (ativos, não expirados, com usos disponíveis)
     * para um cliente específico. Inclui tanto cupons globais (cliente = null) quanto
     * cupons exclusivos do cliente.
     *
     * @param clienteId ID do cliente
     * @return Lista de cupons válidos para o cliente
     * @throws RuntimeException Se o cliente não for encontrado
     */
    public List<Cupom> buscarCuponsValidosParaCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // O repositório aplica as regras de validação (ativo, data, usos, cliente ou global)
        return cupomRepository.findCuponsValidosParaCliente(cliente);
    }

    /**
     * Busca cupons do tipo TROCA ativos para um cliente específico.
     * Útil para exibir o saldo de créditos de troca disponíveis.
     *
     * @param clienteId ID do cliente
     * @return Lista de cupons de troca ativos do cliente
     * @throws RuntimeException Se o cliente não for encontrado
     */
    public List<Cupom> buscarCuponsTrocaAtivosCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // Query que filtra por cliente, tipo TROCA e ativo = true
        return cupomRepository.findByClienteAndTipoAndAtivoTrue(cliente, TipoCupom.TROCA);
    }

    /**
     * Busca cupons PROMOCIONAIS ativos disponíveis para o cliente.
     * Inclui tanto cupons específicos do cliente quanto cupons globais (cliente = null).
     *
     * @param clienteId ID do cliente
     * @return Lista de cupons promocionais válidos
     * @throws RuntimeException Se o cliente não for encontrado
     */
    public List<Cupom> buscarCuponsPromocionaisAtivosCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // Primeiro obtém todos os cupons válidos do cliente, depois filtra apenas os promocionais
        // Usa Stream API do Java 8+ para filtragem em memória (Collection após a query)
        return cupomRepository.findCuponsValidosParaCliente(cliente).stream()
                .filter(cupom -> cupom.getTipo() == TipoCupom.PROMOCIONAL)
                .collect(Collectors.toList());
    }

    /**
     * Verifica se o cliente possui pelo menos um cupom promocional válido disponível.
     *
     * @param clienteId ID do cliente
     * @return true se existe pelo menos um cupom promocional válido, false caso contrário
     * @throws RuntimeException Se o cliente não for encontrado
     */
    public boolean possuiPromocionaisAtivos(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // Conta quantos cupons promocionais válidos o cliente possui
        long count = cupomRepository.findCuponsValidosParaCliente(cliente).stream()
                .filter(cupom -> cupom.getTipo() == TipoCupom.PROMOCIONAL)
                .count();

        return count > 0;
    }

    // ===== CONSUMO E GERENCIAMENTO =====

    /**
     * Consome um cupom, registrando sua utilização.
     * Este método é chamado quando o cliente finaliza uma compra utilizando o cupom.
     *
     * A anotação @Transactional garante que a operação seja atômica e isolada.
     *
     * @param cupomId ID do cupom a ser consumido
     * @throws RuntimeException Se o cupom não for encontrado
     */
    @Transactional
    public void consumirCupom(Long cupomId) {
        Cupom cupom = cupomRepository.findById(cupomId)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado"));

        // O método consumir() da entidade Cupom incrementa o contador de usos e,
        // se atingir o maximoUsos, desativa o cupom automaticamente
        cupom.consumir();

        // Persiste a atualização no banco de dados
        cupomRepository.save(cupom);
    }

    /**
     * Desativa manualmente um cupom, tornando-o indisponível para uso futuro.
     * Útil para campanhas encerradas antecipadamente ou cupons problemáticos.
     *
     * @param cupomId ID do cupom a ser desativado
     * @throws RuntimeException Se o cupom não for encontrado
     */
    @Transactional
    public void desativarCupom(Long cupomId) {
        Cupom cupom = cupomRepository.findById(cupomId)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado"));

        // Muda o status ativo para false
        cupom.setAtivo(false);

        // Persiste a alteração
        cupomRepository.save(cupom);
    }

    /**
     * Remove todos os cupons de um cliente específico.
     * CUIDADO: Esta operação é irreversível e deve ser usada com cautela.
     * Útil em casos de exclusão de conta ou migração de dados.
     *
     * @param clienteId ID do cliente
     * @throws RuntimeException Se o cliente não for encontrado
     */
    public void removerTodosDoCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // O repositório executa DELETE FROM Cupom WHERE cliente = :cliente
        cupomRepository.deleteByCliente(cliente);
    }

    // ===== CRUD BÁSICO =====

    /**
     * Lista todos os cupons cadastrados no sistema (sem filtros).
     * Útil para administração e relatórios.
     *
     * @return Lista com todos os cupons (ativos e inativos)
     */
    public List<Cupom> listarTodos() {
        return cupomRepository.findAll();
    }

    /**
     * Busca um cupom pelo seu ID único.
     *
     * @param id ID do cupom no banco de dados
     * @return Optional contendo o cupom se encontrado, ou vazio caso contrário
     */
    public Optional<Cupom> buscarPorId(Long id) {
        return cupomRepository.findById(id);
    }

    /**
     * Salva um cupom no banco de dados (pode ser criação ou atualização).
     *
     * @param cupom Entidade Cupom a ser persistida
     * @return Cupom persistido com ID gerado (em caso de criação) ou atualizado
     */
    public Cupom salvar(Cupom cupom) {
        return cupomRepository.save(cupom);
    }
}