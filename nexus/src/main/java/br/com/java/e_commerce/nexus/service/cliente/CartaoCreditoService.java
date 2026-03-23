package br.com.java.e_commerce.nexus.service.cliente;

// Importações omitidas para brevidade

import br.com.java.e_commerce.nexus.model.cliente.CartaoCredito;
import br.com.java.e_commerce.nexus.model.enums.BandeiraCartao;
import br.com.java.e_commerce.nexus.repository.cliente.CartaoCreditoRepository;
import br.com.java.e_commerce.nexus.service.exception.CartaoNaoEncontradoException;
import br.com.java.e_commerce.nexus.service.exception.ValidacaoException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CartaoCreditoService {

    // ==================== ATRIBUTO ====================

    /** Repositório para operações de persistência de cartões de crédito */
    private final CartaoCreditoRepository cartaoCreditoRepository;

    /**
     * Construtor para injeção de dependência.
     * O Spring injeta automaticamente a implementação do repositório.
     *
     * @param cartaoCreditoRepository Repositório de cartões de crédito
     */
    public CartaoCreditoService(CartaoCreditoRepository cartaoCreditoRepository) {
        this.cartaoCreditoRepository = cartaoCreditoRepository;
    }

    // ==================== MÉTODOS DE CONSULTA BÁSICA ====================

    /**
     * Retorna todos os cartões de crédito cadastrados no sistema.
     *
     * @return Lista com todos os cartões (sem filtros)
     */
    public List<CartaoCredito> listarTodos() {
        return cartaoCreditoRepository.findAll();
    }

    /**
     * Busca um cartão de crédito pelo seu ID.
     *
     * @param id Identificador único do cartão
     * @return Optional contendo o cartão se encontrado
     * @throws CartaoNaoEncontradoException Se o cartão não existir
     */
    public Optional<CartaoCredito> buscarPorId(Long id) {
        // Utiliza Optional.ofNullable para encapsular o resultado
        // O orElseThrow lança exceção se não encontrar
        return Optional.ofNullable(cartaoCreditoRepository.findById(id)
                .orElseThrow(() -> new CartaoNaoEncontradoException(id)));
    }

    /**
     * Busca todos os cartões de um cliente específico.
     *
     * @param clienteId ID do cliente
     * @return Lista de cartões do cliente
     * @throws ValidacaoException Se o ID do cliente for nulo
     */
    public List<CartaoCredito> buscarPorCliente(Long clienteId) {
        // Validação: ID do cliente é obrigatório
        if (clienteId == null) {
            throw new ValidacaoException("ID do cliente é obrigatório");
        }
        // Delega a consulta para o repositório
        return cartaoCreditoRepository.findByClienteId(clienteId);
    }

    /**
     * Busca todos os cartões de uma determinada bandeira.
     *
     * @param bandeira Bandeira do cartão (Visa, Mastercard, etc.)
     * @return Lista de cartões da bandeira especificada
     * @throws ValidacaoException Se a bandeira for nula
     */
    public List<CartaoCredito> buscarPorBandeira(BandeiraCartao bandeira) {
        if (bandeira == null) {
            throw new ValidacaoException("Bandeira do cartão é obrigatória");
        }
        return cartaoCreditoRepository.findByBandeira(bandeira);
    }

    /**
     * Busca o cartão preferencial de um cliente.
     *
     * Regra de negócio: Cada cliente pode ter apenas um cartão preferencial,
     * que será usado como padrão para pagamentos.
     *
     * @param clienteId ID do cliente
     * @return Optional com o cartão preferencial, ou vazio se não houver
     * @throws ValidacaoException Se o ID do cliente for nulo
     */
    public Optional<CartaoCredito> buscarCartaoPreferencial(Long clienteId) {
        if (clienteId == null) {
            throw new ValidacaoException("ID do cliente é obrigatório");
        }
        // O repositório retorna o cartão preferencial ou null
        return Optional.ofNullable(
                cartaoCreditoRepository.findByClienteIdAndPreferencialTrue(clienteId)
        );
    }

    /**
     * Busca cartões pelos últimos 4 dígitos.
     *
     * Útil para funcionalidades de busca onde o usuário lembra apenas
     * os últimos dígitos do cartão.
     *
     * @param ultimosDigitos String com os últimos dígitos (pode conter máscara)
     * @return Lista de cartões que terminam com os dígitos informados
     * @throws ValidacaoException Se os dígitos não forem fornecidos ou forem inválidos
     */
    public List<CartaoCredito> buscarPorUltimosDigitos(String ultimosDigitos) {
        // Validação: dígitos são obrigatórios
        if (ultimosDigitos == null || ultimosDigitos.trim().isEmpty()) {
            throw new ValidacaoException("Os últimos dígitos do cartão são obrigatórios");
        }

        // Remove caracteres não numéricos (espaços, traços, etc.)
        String digitosLimpos = ultimosDigitos.replaceAll("[^0-9]", "");

        // Validação: exatamente 4 dígitos (padrão de cartões)
        if (digitosLimpos.length() != 4) {
            throw new ValidacaoException("Devem ser fornecidos exatamente 4 dígitos");
        }

        return cartaoCreditoRepository.findByUltimosDigitos(digitosLimpos);
    }

    // ==================== MÉTODOS DE PERSISTÊNCIA ====================

    /**
     * Salva um novo cartão de crédito ou atualiza um existente.
     *
     * Este método aplica diversas regras de negócio:
     * 1. Valida todos os campos do cartão (número, código, bandeira)
     * 2. Verifica se o cartão já existe para o mesmo cliente
     * 3. Gerencia a unicidade do cartão preferencial
     *
     * @param cartao Cartão a ser salvo
     * @return Cartão salvo com ID gerado (se novo) ou atualizado
     * @throws ValidacaoException Se alguma validação falhar
     */
    @Transactional // Garante atomicidade: tudo ou nada
    public CartaoCredito salvar(CartaoCredito cartao) {
        // PASSO 1: Validação completa do cartão
        validarCartao(cartao);

        // PASSO 2: Verificar se já existe cartão com o mesmo número para este cliente
        // Apenas para novos cartões (ID nulo)
        if (cartao.getId() == null && cartao.getCliente() != null) {
            validarCartaoUnicoPorCliente(cartao);
        }

        // PASSO 3: Gerenciar cartão preferencial
        // Se este cartão é marcado como preferencial, remove a flag de outros cartões
        // do mesmo cliente (garantindo que apenas um seja preferencial)
        if (cartao.isPreferencial() && cartao.getCliente() != null) {
            removerPreferencialDeOutrosCartoes(cartao);
        }

        // PASSO 4: Persistir no banco de dados
        return cartaoCreditoRepository.save(cartao);
    }

    /**
     * Valida se um cartão já está cadastrado para o mesmo cliente.
     *
     * Esta validação evita que um cliente cadastre o mesmo cartão duas vezes.
     *
     * @param cartaoNovo Cartão a ser validado
     * @throws ValidacaoException Se o cartão já existir para o cliente
     */
    private void validarCartaoUnicoPorCliente(CartaoCredito cartaoNovo) {
        // Remove máscara do número do cartão novo
        String numeroLimpo = cartaoNovo.getNumeroCartao().replaceAll("[^0-9]", "");

        // Busca todos os cartões do cliente e verifica se algum tem o mesmo número
        boolean cartaoExistente = cartaoCreditoRepository
                .findByClienteId(cartaoNovo.getCliente().getId())
                .stream()
                .anyMatch(c -> {
                    String numeroExistente = c.getNumeroCartao().replaceAll("[^0-9]", "");
                    return numeroExistente.equals(numeroLimpo);
                });

        if (cartaoExistente) {
            throw new ValidacaoException("Este cartão já está cadastrado para o cliente");
        }
    }

    /**
     * Remove a flag de preferencial de todos os outros cartões do mesmo cliente.
     *
     * Esta lógica garante que apenas um cartão por cliente seja marcado como
     * preferencial, que é uma regra de negócio fundamental.
     *
     * @param cartaoNovo Cartão que está sendo definido como preferencial
     */
    private void removerPreferencialDeOutrosCartoes(CartaoCredito cartaoNovo) {
        // Busca todos os cartões do cliente
        List<CartaoCredito> cartoesCliente = cartaoCreditoRepository
                .findByClienteId(cartaoNovo.getCliente().getId());

        // Para cada cartão que é preferencial e não é o cartão atual
        cartoesCliente.stream()
                .filter(c -> c.isPreferencial() &&
                        // Se o cartão novo ainda não tem ID (novo) ou tem ID diferente
                        (cartaoNovo.getId() == null || !c.getId().equals(cartaoNovo.getId())))
                .forEach(c -> {
                    // Remove a flag preferencial
                    c.setPreferencial(false);
                    // Salva a alteração
                    cartaoCreditoRepository.save(c);
                });
    }

    // ==================== MÉTODOS DE VALIDAÇÃO ====================

    /**
     * Valida todos os campos obrigatórios e regras de formato de um cartão.
     *
     * Regras de validação implementadas:
     * - Número do cartão: 16 dígitos numéricos
     * - Nome impresso: não vazio, máximo 50 caracteres
     * - Código de segurança: 3 dígitos numéricos
     * - Bandeira: obrigatória e válida
     * - Cliente: obrigatório para novos cartões
     *
     * @param cartao Cartão a ser validado
     * @throws ValidacaoException Se alguma regra for violada
     */
    public void validarCartao(CartaoCredito cartao) {
        // ========== VALIDAÇÃO DE NULIDADE ==========
        if (cartao == null) {
            throw new ValidacaoException("Cartão de crédito não pode ser nulo");
        }

        // ========== VALIDAÇÃO DO NÚMERO DO CARTÃO ==========
        if (cartao.getNumeroCartao() == null) {
            throw new ValidacaoException("Número do cartão é obrigatório");
        }

        // Remove caracteres não numéricos para validação
        String numeroLimpo = cartao.getNumeroCartao().replaceAll("[^0-9]", "");

        if (numeroLimpo.isEmpty()) {
            throw new ValidacaoException("Número do cartão deve conter apenas dígitos");
        }

        // Padrão de cartões de crédito: 16 dígitos
        if (numeroLimpo.length() != 16) {
            throw new ValidacaoException("Número do cartão deve ter 16 dígitos");
        }

        // ========== VALIDAÇÃO DO NOME IMPRESSO ==========
        if (cartao.getNomeImpresso() == null || cartao.getNomeImpresso().trim().isEmpty()) {
            throw new ValidacaoException("Nome impresso no cartão é obrigatório");
        }

        // Limite de caracteres para o campo de nome
        if (cartao.getNomeImpresso().length() > 50) {
            throw new ValidacaoException("Nome impresso não pode ter mais que 50 caracteres");
        }

        // ========== VALIDAÇÃO DO CÓDIGO DE SEGURANÇA (CVV) ==========
        if (cartao.getCodigoSeguranca() == null) {
            throw new ValidacaoException("Código de segurança é obrigatório");
        }

        String codigoLimpo = cartao.getCodigoSeguranca().replaceAll("[^0-9]", "");

        if (codigoLimpo.isEmpty()) {
            throw new ValidacaoException("Código de segurança deve conter apenas dígitos");
        }

        // Padrão de CVV: 3 dígitos
        if (codigoLimpo.length() != 3) {
            throw new ValidacaoException("Código de segurança deve ter 3 dígitos");
        }

        // ========== VALIDAÇÃO DA BANDEIRA ==========
        if (cartao.getBandeira() == null) {
            throw new ValidacaoException("Bandeira do cartão é obrigatória");
        }

        // ========== VALIDAÇÃO DO CLIENTE ==========
        // Para operações de cadastro (cartão novo sem ID), o cliente é obrigatório
        if (cartao.getCliente() == null && cartao.getId() == null) {
            throw new ValidacaoException("Cartão deve estar associado a um cliente");
        }
    }

    // ==================== GERENCIAMENTO DE CARTÃO PREFERENCIAL ====================

    /**
     * Define um cartão como preferencial para o cliente.
     *
     * Esta operação:
     * 1. Busca o cartão pelo ID
     * 2. Verifica se está associado a um cliente
     * 3. Remove a flag preferencial de todos os outros cartões do cliente
     * 4. Define o cartão especificado como preferencial
     *
     * @param id ID do cartão a ser definido como preferencial
     * @return Cartão atualizado
     * @throws ValidacaoException Se o ID for nulo ou cartão não tiver cliente
     * @throws CartaoNaoEncontradoException Se o cartão não existir
     */
    @Transactional
    public CartaoCredito definirComoPreferencial(Long id) {
        // Validação: ID é obrigatório
        if (id == null) {
            throw new ValidacaoException("ID do cartão é obrigatório");
        }

        // Busca o cartão, lança exceção se não existir
        CartaoCredito cartao = cartaoCreditoRepository.findById(id)
                .orElseThrow(() -> new CartaoNaoEncontradoException(id));

        // Verifica se o cartão está associado a um cliente
        if (cartao.getCliente() == null) {
            throw new ValidacaoException("Cartão não está associado a nenhum cliente");
        }

        // PASSO 1: Remove a flag preferencial de todos os outros cartões do mesmo cliente
        List<CartaoCredito> cartoesCliente = cartaoCreditoRepository
                .findByClienteId(cartao.getCliente().getId());

        cartoesCliente.forEach(c -> {
            // Se o cartão é preferencial e não é o cartão que está sendo definido
            if (c.isPreferencial() && !c.getId().equals(id)) {
                c.setPreferencial(false);
                cartaoCreditoRepository.save(c); // Persiste a alteração
            }
        });

        // PASSO 2: Define o cartão especificado como preferencial
        cartao.setPreferencial(true);

        // PASSO 3: Persiste a alteração e retorna
        return cartaoCreditoRepository.save(cartao);
    }

    // ==================== MASCARAMENTO DE DADOS (SEGURANÇA) ====================

    /**
     * Mascara o número do cartão para exibição segura.
     *
     * Formato de saída: "**** **** **** 1234" (mostra apenas os últimos 4 dígitos)
     *
     * @param numeroCartao Número completo do cartão (pode estar com máscara)
     * @return Número mascarado ou null se entrada for null
     */
    public String mascararNumeroCartao(String numeroCartao) {
        if (numeroCartao == null) {
            return null;
        }

        // Remove todos os caracteres não numéricos
        String numeroLimpo = numeroCartao.replaceAll("[^0-9]", "");

        // Se não tiver 16 dígitos, retorna o original (não consegue mascarar)
        if (numeroLimpo.length() < 16) {
            return numeroCartao;
        }

        // Retorna apenas os últimos 4 dígitos com máscara
        return "**** **** **** " + numeroLimpo.substring(numeroLimpo.length() - 4);
    }

    /**
     * Lista todos os cartões com números mascarados.
     *
     * Utilizado para exibição em listagens onde não se deve expor
     * o número completo do cartão por questões de segurança.
     *
     * @return Lista de cartões com números mascarados
     */
    public List<CartaoCredito> listarTodosMascarados() {
        // Busca todos os cartões
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findAll();

        // Aplica mascaramento em cada cartão
        cartoes.forEach(c ->
                c.setNumeroCartao(mascararNumeroCartao(c.getNumeroCartao()))
        );

        return cartoes;
    }

    /**
     * Busca cartões de um cliente com números mascarados.
     *
     * @param clienteId ID do cliente
     * @return Lista de cartões do cliente com números mascarados
     * @throws ValidacaoException Se o ID do cliente for nulo
     */
    public List<CartaoCredito> buscarPorClienteMascarados(Long clienteId) {
        // Busca cartões do cliente
        List<CartaoCredito> cartoes = buscarPorCliente(clienteId);

        // Aplica mascaramento em cada cartão
        cartoes.forEach(c ->
                c.setNumeroCartao(mascararNumeroCartao(c.getNumeroCartao()))
        );

        return cartoes;
    }

    // ==================== MÉTODOS DE EXCLUSÃO ====================

    /**
     * Exclui um cartão de crédito permanentemente.
     *
     * Regras de negócio para exclusão:
     * - Não permite excluir se for o único cartão preferencial do cliente
     * - O cliente deve ter pelo menos um cartão preferencial sempre
     *
     * @param id ID do cartão a ser excluído
     * @throws ValidacaoException Se o ID for nulo ou for o único cartão preferencial
     * @throws CartaoNaoEncontradoException Se o cartão não existir
     */
    @Transactional
    public void excluir(Long id) {
        // Validação: ID é obrigatório
        if (id == null) {
            throw new ValidacaoException("ID do cartão é obrigatório");
        }

        // Busca o cartão, lança exceção se não existir
        CartaoCredito cartao = cartaoCreditoRepository.findById(id)
                .orElseThrow(() -> new CartaoNaoEncontradoException(id));

        // VALIDAÇÃO CRÍTICA: Verificar se é o único cartão preferencial
        if (cartao.isPreferencial() && cartao.getCliente() != null) {
            // Conta quantos outros cartões preferenciais existem para este cliente
            long outrosPreferenciais = cartaoCreditoRepository
                    .findByClienteId(cartao.getCliente().getId())
                    .stream()
                    .filter(c -> c.isPreferencial() && !c.getId().equals(id))
                    .count();

            // Se não há outro cartão preferencial, não permite exclusão
            if (outrosPreferenciais == 0) {
                throw new ValidacaoException(
                        "Não é possível excluir o único cartão preferencial do cliente"
                );
            }
        }

        // Exclui o cartão
        cartaoCreditoRepository.delete(cartao);
    }

    /**
     * Exclui todos os cartões de um cliente.
     *
     * Útil para operações de limpeza em massa ou quando um cliente
     * é excluído permanentemente do sistema.
     *
     * @param clienteId ID do cliente
     * @throws ValidacaoException Se o ID do cliente for nulo
     */
    @Transactional
    public void excluirTodosDoCliente(Long clienteId) {
        // Validação: ID do cliente é obrigatório
        if (clienteId == null) {
            throw new ValidacaoException("ID do cliente é obrigatório");
        }

        // Busca todos os cartões do cliente
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findByClienteId(clienteId);

        // Se houver cartões, exclui todos em lote
        if (!cartoes.isEmpty()) {
            cartaoCreditoRepository.deleteAll(cartoes);
        }
    }

    // ==================== MÉTODOS AUXILIARES DE CONSULTA ====================

    /**
     * Conta quantos cartões um cliente possui.
     *
     * @param clienteId ID do cliente
     * @return Número de cartões do cliente
     */
    public long contarCartoesPorCliente(Long clienteId) {
        // Se clienteId for nulo, retorna 0 (segurança)
        if (clienteId == null) {
            return 0;
        }
        // Busca todos e conta (pode ser otimizado com query count)
        return cartaoCreditoRepository.findByClienteId(clienteId).size();
    }

    /**
     * Verifica se um cliente possui algum cartão preferencial.
     *
     * @param clienteId ID do cliente
     * @return true se existir cartão preferencial, false caso contrário
     */
    public boolean existeCartaoPreferencial(Long clienteId) {
        // Se clienteId for nulo, retorna false (segurança)
        if (clienteId == null) {
            return false;
        }
        // Utiliza método específico do repositório para verificar existência
        return cartaoCreditoRepository.existsByClienteIdAndPreferencialTrue(clienteId);
    }
}