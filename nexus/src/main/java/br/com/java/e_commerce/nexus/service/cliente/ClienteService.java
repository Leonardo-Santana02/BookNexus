package br.com.java.e_commerce.nexus.service.cliente;

import br.com.java.e_commerce.nexus.model.cliente.CartaoCredito;
import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.cliente.Telefone;
import br.com.java.e_commerce.nexus.model.enums.Genero;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.cliente.EnderecoRepository;
import br.com.java.e_commerce.nexus.repository.cliente.TelefoneRepository;
import br.com.java.e_commerce.nexus.service.exception.ClienteNaoEncontradoException;
import br.com.java.e_commerce.nexus.service.exception.CpfJaCadastradoException;
import br.com.java.e_commerce.nexus.service.exception.EmailJaCadastradoException;
import br.com.java.e_commerce.nexus.service.exception.ValidacaoException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class ClienteService {

    // ==================== ATRIBUTOS ====================
    /** Repositório para operações CRUD da entidade Cliente */
    private final ClienteRepository clienteRepository;

    /** Repositório para operações CRUD da entidade Endereco */
    private final EnderecoRepository enderecoRepository;

    /** Repositório para operações CRUD da entidade Telefone */
    private final TelefoneRepository telefoneRepository;

    /** Serviço especializado para operações com cartões de crédito */
    private final CartaoCreditoService cartaoCreditoService;

    /** Componente do Spring Security para codificação/decodificação de senhas */
    private final PasswordEncoder passwordEncoder;

    /**
     * Construtor para injeção de dependências.
     * O Spring automaticamente fornecerá as implementações concretas de cada dependência.
     *
     * @param clienteRepository Repositório de clientes
     * @param enderecoRepository Repositório de endereços
     * @param telefoneRepository Repositório de telefones
     * @param cartaoCreditoService Serviço de cartões de crédito
     * @param passwordEncoder Codificador de senhas
     */
    public ClienteService(ClienteRepository clienteRepository,
                          EnderecoRepository enderecoRepository,
                          TelefoneRepository telefoneRepository,
                          CartaoCreditoService cartaoCreditoService,
                          PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.enderecoRepository = enderecoRepository;
        this.telefoneRepository = telefoneRepository;
        this.cartaoCreditoService = cartaoCreditoService;
        this.passwordEncoder = passwordEncoder;
    }

    // ==================== MÉTODOS DE CONSULTA BÁSICA ====================

    /**
     * Retorna todos os clientes cadastrados no sistema, independente do status.
     *
     * @return Lista com todos os clientes (ativos e inativos)
     */
    public List<Cliente> listarTodos() {
        // Delegar para o método findAll() do repositório JPA
        return clienteRepository.findAll();
    }

    /**
     * Retorna apenas os clientes ativos (não inativados).
     * Utiliza query derivada do Spring Data JPA.
     *
     * @return Lista de clientes com inativado = false
     */
    public List<Cliente> listarAtivos() {
        return clienteRepository.findByInativadoFalse();
    }

    /**
     * Retorna apenas os clientes inativados (exclusão lógica).
     * Utiliza query derivada do Spring Data JPA.
     *
     * @return Lista de clientes com inativado = true
     */
    public List<Cliente> listarInativos() {
        return clienteRepository.findByInativadoTrue();
    }

    /**
     * Busca um cliente pelo seu ID.
     * Retorna um Optional para evitar NullPointerException.
     *
     * @param id Identificador único do cliente
     * @return Optional contendo o cliente se encontrado, ou vazio caso contrário
     */
    public Optional<Cliente> buscarPorId(Long id) {
        // Utiliza o método findById e lança exceção customizada se não encontrar
        // O Optional.ofNullable envolve o resultado, permitindo tratamento seguro
        return Optional.ofNullable(clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(id)));
    }

    /**
     * Busca um cliente com todos os seus relacionamentos carregados (endereços, telefones, cartões).
     * Utiliza @Transactional(readOnly = true) para otimizar a consulta.
     *
     * @param id Identificador do cliente
     * @return Cliente completo com todos os relacionamentos inicializados
     * @throws ClienteNaoEncontradoException Se o cliente não existir
     */
    @Transactional(readOnly = true) // readOnly = true otimiza a transação para apenas leitura
    public Cliente buscarClienteCompleto(Long id) {
        // Primeiro busca o cliente básico
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(id));

        // Forçar carregamento dos relacionamentos LAZY (carregamento preguiçoso)
        // Acessar os tamanhos das coleções força o Hibernate a carregar os dados do banco
        // Isso evita o problema de LazyInitializationException quando a sessão for fechada
        cliente.getEnderecos().size();   // Carrega todos os endereços
        cliente.getTelefones().size();   // Carrega todos os telefones
        cliente.getCartoesCredito().size(); // Carrega todos os cartões

        return cliente;
    }

    /**
     * Busca clientes pelo nome (busca parcial, case-insensitive).
     * Se o nome não for fornecido ou estiver vazio, retorna todos os clientes ativos.
     *
     * @param nome Nome ou parte do nome para busca
     * @return Lista de clientes que correspondem ao critério
     */
    public List<Cliente> buscarPorNome(String nome) {
        // Validação: se nome for nulo ou apenas espaços em branco
        if (nome == null || nome.trim().isEmpty()) {
            // Retorna todos os clientes ativos como fallback
            return listarAtivos();
        }
        // Utiliza query derivada para busca case-insensitive com like
        return clienteRepository.findByNomeContainingIgnoreCase(nome.trim());
    }

    // ==================== MÉTODOS DE PERSISTÊNCIA ====================

    /**
     * Salva um novo cliente no sistema.
     * Este método realiza validações extensivas, criptografa a senha,
     * filtra dados inválidos e gerencia os relacionamentos.
     *
     * @param cliente Objeto cliente a ser persistido
     * @return Cliente salvo com seu ID gerado
     * @throws CpfJaCadastradoException Se o CPF já existe no sistema
     * @throws EmailJaCadastradoException Se o email já existe no sistema
     * @throws ValidacaoException Se alguma validação de negócio falhar
     */
    @Transactional // Garante que toda a operação seja atômica (tudo ou nada)
    public Cliente salvar(Cliente cliente) {
        // LOGS DE DEBUG para acompanhamento do processo
        System.out.println("=== SERVICE: SALVAR CLIENTE ===");
        System.out.println("Nome: " + cliente.getNome());
        System.out.println("CPF: " + cliente.getCpf());
        System.out.println("Email: " + cliente.getEmail());
        System.out.println("Endereços recebidos: " + (cliente.getEnderecos() != null ? cliente.getEnderecos().size() : 0));
        System.out.println("Telefones recebidos: " + (cliente.getTelefones() != null ? cliente.getTelefones().size() : 0));
        System.out.println("Cartões recebidos: " + (cliente.getCartoesCredito() != null ? cliente.getCartoesCredito().size() : 0));

        // PASSO 1: Validações básicas de negócio
        validarCliente(cliente);

        // PASSO 2: Verificações de unicidade - impedem duplicidade de dados críticos
        // Verifica se CPF já está cadastrado
        if (clienteRepository.existsByCpf(cliente.getCpf())) {
            System.out.println("CPF já existe: " + cliente.getCpf());
            throw new CpfJaCadastradoException(cliente.getCpf());
        }
        // Verifica se email já está cadastrado
        if (clienteRepository.existsByEmail(cliente.getEmail())) {
            System.out.println("Email já existe: " + cliente.getEmail());
            throw new EmailJaCadastradoException(cliente.getEmail());
        }

        // PASSO 3: Criptografar a senha - NUNCA armazenar senhas em texto plano
        if (cliente.getSenha() != null && !cliente.getSenha().isEmpty()) {
            // Utiliza PasswordEncoder para gerar hash seguro (BCrypt normalmente)
            cliente.setSenha(passwordEncoder.encode(cliente.getSenha()));
            System.out.println("Senha criptografada");
        } else {
            throw new ValidacaoException("Senha é obrigatória");
        }

        // PASSO 4: Filtrar endereços válidos - apenas os completamente preenchidos
        List<Endereco> enderecosValidos = filtrarEnderecosValidos(cliente.getEnderecos());
        cliente.setEnderecos(enderecosValidos);
        System.out.println("Endereços válidos após filtro: " + enderecosValidos.size());

        // PASSO 5: Configurar os relacionamentos bidirecionais e limpar máscaras
        configurarRelacionamentos(cliente);

        // PASSO 6: Validações específicas de negócio
        validarEnderecosCliente(cliente);    // Verifica tipos de endereço (entrega/cobrança)
        validarCartaoPreferencial(cliente);  // Verifica se há exatamente um cartão preferencial

        // PASSO 7: Persistir no banco de dados
        try {
            Cliente clienteSalvo = clienteRepository.save(cliente);
            System.out.println("Cliente salvo com ID: " + clienteSalvo.getId());

            // PASSO 8: Salvar explicitamente os relacionamentos (garantia adicional)
            salvarRelacionamentos(clienteSalvo);

            return clienteSalvo;

        } catch (Exception e) {
            // Captura e loga qualquer exceção durante a persistência
            System.out.println("ERRO ao salvar cliente: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-lança a exceção para ser tratada pela camada superior
        }
    }

    /**
     * Atualiza um cliente existente no sistema.
     * Mantém os relacionamentos existentes e atualiza apenas o que foi modificado.
     *
     * @param cliente Cliente com os dados atualizados
     * @return Cliente atualizado
     * @throws ClienteNaoEncontradoException Se o cliente não existir
     */
    @Transactional
    public Cliente salvarExistente(Cliente cliente) {
        System.out.println("=== SERVICE: SALVAR CLIENTE EXISTENTE ===");
        System.out.println("ID: " + cliente.getId());
        System.out.println("Nome: " + cliente.getNome());

        // PASSO 1: Validações básicas
        validarCliente(cliente);

        // PASSO 2: Verificar se o cliente existe no banco
        Cliente clienteExistente = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new ClienteNaoEncontradoException(cliente.getId()));

        System.out.println("Cliente existente encontrado: " + clienteExistente.getId());

        // PASSO 3: Verificações de unicidade para CPF e email
        // Só valida se os valores foram alterados, evitando conflitos com os dados existentes

        // Verifica CPF alterado e se o novo CPF já pertence a outro cliente
        if (!clienteExistente.getCpf().equals(cliente.getCpf()) &&
                clienteRepository.existsByCpf(cliente.getCpf())) {
            System.out.println("CPF já existe e é diferente: " + cliente.getCpf());
            throw new CpfJaCadastradoException(cliente.getCpf());
        }

        // Verifica email alterado e se o novo email já pertence a outro cliente
        if (!clienteExistente.getEmail().equals(cliente.getEmail()) &&
                clienteRepository.existsByEmail(cliente.getEmail())) {
            System.out.println("Email já existe e é diferente: " + cliente.getEmail());
            throw new EmailJaCadastradoException(cliente.getEmail());
        }

        // PASSO 4: Atualizar senha apenas se foi fornecida e é diferente da atual
        atualizarSenhaSeNecessario(cliente, clienteExistente);

        // PASSO 5: Atualizar dados básicos (campos simples)
        clienteExistente.setNome(cliente.getNome());
        clienteExistente.setCpf(cliente.getCpf());
        clienteExistente.setEmail(cliente.getEmail());
        clienteExistente.setGenero(cliente.getGenero());
        clienteExistente.setDataNascimento(cliente.getDataNascimento());
        clienteExistente.setInativado(cliente.getInativado());

        // PASSO 6: Atualizar relacionamentos (endereços, telefones, cartões)
        atualizarEnderecos(cliente, clienteExistente);
        atualizarTelefones(cliente, clienteExistente);
        atualizarCartoesCredito(cliente, clienteExistente);

        // PASSO 7: Validações de negócio após as atualizações
        validarEnderecosCliente(clienteExistente);
        validarCartaoPreferencial(clienteExistente);

        // PASSO 8: Persistir as alterações
        Cliente clienteAtualizado = clienteRepository.save(clienteExistente);
        System.out.println("Cliente atualizado com sucesso: " + clienteAtualizado.getId());

        return clienteAtualizado;
    }

    /**
     * Atualiza a senha do cliente apenas se uma nova senha foi fornecida
     * e ela é diferente da senha atual armazenada.
     *
     * @param cliente Cliente com os dados de entrada
     * @param clienteExistente Cliente existente no banco
     */
    private void atualizarSenhaSeNecessario(Cliente cliente, Cliente clienteExistente) {
        if (cliente.getSenha() != null && !cliente.getSenha().isEmpty()) {
            System.out.println("Atualizando senha...");
            // Verifica se a senha fornecida é diferente da armazenada
            // passwordEncoder.matches() compara texto plano com hash armazenado
            if (!passwordEncoder.matches(cliente.getSenha(), clienteExistente.getSenha())) {
                clienteExistente.setSenha(passwordEncoder.encode(cliente.getSenha()));
                System.out.println("Nova senha criptografada");
            }
        }
        // Se não foi fornecida, mantém a senha existente (não altera)
    }

    /**
     * Atualiza a lista de endereços do cliente.
     * Remove todos os endereços antigos e adiciona os novos.
     *
     * @param cliente Cliente com os novos endereços
     * @param clienteExistente Cliente existente para atualizar
     */
    private void atualizarEnderecos(Cliente cliente, Cliente clienteExistente) {
        System.out.println("Atualizando endereços...");

        // Remove todos os endereços antigos do banco de dados
        enderecoRepository.deleteAll(clienteExistente.getEnderecos());
        clienteExistente.getEnderecos().clear();

        // Filtra apenas endereços válidos (completamente preenchidos)
        List<Endereco> enderecosValidos = filtrarEnderecosValidos(cliente.getEnderecos());
        System.out.println("Novos endereços válidos: " + enderecosValidos.size());

        // Adiciona os novos endereços, garantindo que são novas entidades (ID = null)
        for (Endereco endereco : enderecosValidos) {
            endereco.setId(null); // Força a criação de novas entidades, não atualização
            endereco.setCliente(clienteExistente);
            clienteExistente.getEnderecos().add(endereco);
        }
    }

    /**
     * Atualiza a lista de telefones do cliente.
     * Remove todos os telefones antigos e adiciona apenas os válidos.
     * Realiza limpeza de máscaras e validação rigorosa dos campos.
     *
     * @param cliente Cliente com os novos telefones
     * @param clienteExistente Cliente existente para atualizar
     */
    private void atualizarTelefones(Cliente cliente, Cliente clienteExistente) {
        System.out.println("Atualizando telefones...");

        // Remove todos os telefones antigos
        telefoneRepository.deleteAll(clienteExistente.getTelefones());
        clienteExistente.getTelefones().clear();

        // Lista para armazenar apenas telefones válidos
        List<Telefone> telefonesValidos = new ArrayList<>();

        // Processa cada telefone recebido
        for (Telefone telefone : cliente.getTelefones()) {
            if (telefone == null) continue; // Ignora telefones nulos

            // Limpar máscaras: remove tudo que não é número (espaços, parênteses, hífens, etc.)
            if (telefone.getDdd() != null) {
                telefone.setDdd(telefone.getDdd().replaceAll("[^0-9]", ""));
            }
            if (telefone.getNumero() != null) {
                telefone.setNumero(telefone.getNumero().replaceAll("[^0-9]", ""));
            }

            // VALIDAÇÃO RIGOROSA: todos os campos obrigatórios devem estar preenchidos
            if (notBlank(telefone.getDdd()) &&
                    notBlank(telefone.getNumero()) &&
                    telefone.getTipo() != null) {

                telefone.setId(null); // Força criação de nova entidade
                telefone.setCliente(clienteExistente);
                telefonesValidos.add(telefone);

                System.out.println("Telefone válido: DDD=" + telefone.getDdd() +
                        ", Número=" + telefone.getNumero() +
                        ", Tipo=" + telefone.getTipo());
            } else {
                System.out.println("Telefone ignorado - campos incompletos: " +
                        "DDD=" + telefone.getDdd() +
                        ", Número=" + telefone.getNumero() +
                        ", Tipo=" + telefone.getTipo());
            }
        }

        System.out.println("Telefones válidos após filtro: " + telefonesValidos.size());
        clienteExistente.getTelefones().addAll(telefonesValidos);
    }

    /**
     * Atualiza a lista de cartões de crédito do cliente.
     * Remove todos os cartões antigos e adiciona apenas os válidos.
     * Realiza validações adicionais através do CartaoCreditoService.
     *
     * @param cliente Cliente com os novos cartões
     * @param clienteExistente Cliente existente para atualizar
     */
    private void atualizarCartoesCredito(Cliente cliente, Cliente clienteExistente) {
        System.out.println("Atualizando cartões...");

        // Limpa todos os cartões antigos
        clienteExistente.getCartoesCredito().clear();

        List<CartaoCredito> cartoesValidos = new ArrayList<>();

        for (CartaoCredito cartao : cliente.getCartoesCredito()) {
            if (cartao == null) continue;

            // Limpar máscaras: números do cartão e código de segurança
            if (cartao.getNumeroCartao() != null) {
                cartao.setNumeroCartao(cartao.getNumeroCartao().replaceAll("[^0-9]", ""));
            }
            if (cartao.getCodigoSeguranca() != null) {
                cartao.setCodigoSeguranca(cartao.getCodigoSeguranca().replaceAll("[^0-9]", ""));
            }

            // Valida todos os campos obrigatórios do cartão
            if (notBlank(cartao.getNumeroCartao()) &&
                    notBlank(cartao.getNomeImpresso()) &&
                    notBlank(cartao.getCodigoSeguranca()) &&
                    cartao.getBandeira() != null) {

                cartao.setId(null); // Força criação de nova entidade
                cartao.setCliente(clienteExistente);
                cartoesValidos.add(cartao);

                // Validações específicas de cartão (validade, algoritmo de Luhn, etc.)
                cartaoCreditoService.validarCartao(cartao);
            } else {
                System.out.println("Cartão ignorado - campos incompletos");
            }
        }

        System.out.println("Cartões válidos após filtro: " + cartoesValidos.size());
        clienteExistente.getCartoesCredito().addAll(cartoesValidos);
    }

    // ==================== MÉTODOS DE VALIDAÇÃO ====================

    /**
     * Valida todos os campos obrigatórios e regras de negócio do cliente.
     * Este método é chamado tanto para criação quanto para atualização.
     *
     * @param cliente Objeto cliente a ser validado
     * @throws ValidacaoException Se alguma regra de validação for violada
     */
    private void validarCliente(Cliente cliente) {
        // VALIDAÇÃO DE ENDEREÇOS
        // Verifica se há pelo menos um endereço preenchido
        List<Endereco> enderecosPreenchidos = filtrarEnderecosPreenchidos(cliente.getEnderecos());
        if (enderecosPreenchidos.isEmpty()) {
            throw new ValidacaoException("Cliente deve ter pelo menos um endereço");
        }

        // VALIDAÇÃO DE TELEFONES
        // Verifica se há pelo menos um telefone COMPLETO (DDD, número e tipo)
        List<Telefone> telefonesCompletos = cliente.getTelefones().stream()
                .filter(t -> t != null &&
                        notBlank(t.getDdd()) &&
                        notBlank(t.getNumero()) &&
                        t.getTipo() != null)
                .collect(Collectors.toList());

        if (telefonesCompletos.isEmpty()) {
            throw new ValidacaoException("Cliente deve ter pelo menos um telefone com DDD, número e tipo preenchidos");
        }

        // VALIDAÇÃO DE CARTÕES
        // Verifica se há pelo menos um cartão COMPLETO (todos os campos preenchidos)
        List<CartaoCredito> cartoesCompletos = cliente.getCartoesCredito().stream()
                .filter(c -> c != null &&
                        notBlank(c.getNumeroCartao()) &&
                        notBlank(c.getNomeImpresso()) &&
                        notBlank(c.getCodigoSeguranca()) &&
                        c.getBandeira() != null)
                .collect(Collectors.toList());

        if (cartoesCompletos.isEmpty()) {
            throw new ValidacaoException("Cliente deve ter pelo menos um cartão de crédito com todos os campos preenchidos");
        }

        // VALIDAÇÃO DE CAMPOS SIMPLES
        // Nome obrigatório
        if (cliente.getNome() == null || cliente.getNome().trim().isEmpty()) {
            throw new ValidacaoException("Nome do cliente é obrigatório");
        }

        // CPF obrigatório e com formato válido
        if (cliente.getCpf() == null || !validarCPF(cliente.getCpf())) {
            throw new ValidacaoException("CPF inválido");
        }

        // Email obrigatório e com formato válido (regex simples)
        if (cliente.getEmail() == null || !cliente.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ValidacaoException("Email inválido");
        }

        // Data de nascimento obrigatória
        if (cliente.getDataNascimento() == null) {
            throw new ValidacaoException("Data de nascimento é obrigatória");
        }

        // Gênero obrigatório
        if (cliente.getGenero() == null) {
            throw new ValidacaoException("Gênero é obrigatório");
        }

        // VALIDAÇÃO DE SENHA
        // Para novos clientes (ID nulo), senha é obrigatória
        if (cliente.getId() == null && (cliente.getSenha() == null || cliente.getSenha().trim().isEmpty())) {
            throw new ValidacaoException("Senha é obrigatória para novos clientes");
        }

        // Tamanho mínimo de senha (requisito de segurança)
        if (cliente.getSenha() != null && cliente.getSenha().length() < 6) {
            throw new ValidacaoException("A senha deve ter pelo menos 6 caracteres");
        }
    }

    /**
     * Valida se o cliente possui endereços de entrega e cobrança.
     * Regra de negócio: todo cliente deve ter pelo menos um endereço
     * configurado para entrega e um para cobrança.
     *
     * @param cliente Cliente a ser validado
     * @throws ValidacaoException Se faltar endereço de entrega ou cobrança
     */
    private void validarEnderecosCliente(Cliente cliente) {
        // Considera apenas endereços preenchidos (não vazios)
        List<Endereco> enderecosPreenchidos = filtrarEnderecosPreenchidos(cliente.getEnderecos());

        // Verifica existência de endereço marcado como entrega
        boolean temEnderecoEntrega = enderecosPreenchidos.stream()
                .anyMatch(Endereco::isEnderecoEntrega);

        // Verifica existência de endereço marcado como cobrança
        boolean temEnderecoCobranca = enderecosPreenchidos.stream()
                .anyMatch(Endereco::isEnderecoCobranca);

        if (!temEnderecoEntrega) {
            throw new ValidacaoException("Cliente deve ter pelo menos um endereço de entrega");
        }
        if (!temEnderecoCobranca) {
            throw new ValidacaoException("Cliente deve ter pelo menos um endereço de cobrança");
        }
    }

    /**
     * Valida a regra de negócio: deve haver exatamente um cartão preferencial.
     * Cartão preferencial é utilizado como padrão para pagamentos.
     *
     * @param cliente Cliente a ser validado
     * @throws ValidacaoException Se não houver exatamente um cartão preferencial
     */
    private void validarCartaoPreferencial(Cliente cliente) {
        // Considera apenas cartões completamente preenchidos
        List<CartaoCredito> cartoesPreenchidos = filtrarCartoesPreenchidos(cliente.getCartoesCredito());

        // Conta quantos cartões estão marcados como preferencial
        long cartoesPreferenciais = cartoesPreenchidos.stream()
                .filter(CartaoCredito::isPreferencial)
                .count();

        if (cartoesPreferenciais != 1) {
            throw new ValidacaoException("Deve haver exatamente um cartão preferencial");
        }
    }

    // ==================== MÉTODOS AUXILIARES DE FILTRAGEM ====================

    /**
     * Filtra endereços que possuem pelo menos um campo preenchido.
     * Utilizado para validação de existência de endereço.
     *
     * @param enderecos Lista de endereços
     * @return Lista de endereços com pelo menos um campo não vazio
     */
    private List<Endereco> filtrarEnderecosPreenchidos(List<Endereco> enderecos) {
        if (enderecos == null) return List.of(); // Retorna lista vazia se nulo

        return enderecos.stream()
                .filter(e -> e != null && (
                        e.getTipoLogradouro() != null ||
                                notBlank(e.getRua()) ||
                                notBlank(e.getNumero()) ||
                                notBlank(e.getBairro()) ||
                                notBlank(e.getCidade()) ||
                                e.getUf() != null ||
                                notBlank(e.getCep())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Filtra telefones que possuem pelo menos um campo preenchido.
     *
     * @param telefones Lista de telefones
     * @return Lista de telefones com pelo menos um campo não vazio
     */
    private List<Telefone> filtrarTelefonesPreenchidos(List<Telefone> telefones) {
        if (telefones == null) return List.of();

        return telefones.stream()
                .filter(t -> t != null && (
                        notBlank(t.getDdd()) ||
                                notBlank(t.getNumero()) ||
                                t.getTipo() != null
                ))
                .collect(Collectors.toList());
    }

    /**
     * Filtra cartões que possuem pelo menos um campo preenchido.
     *
     * @param cartoes Lista de cartões
     * @return Lista de cartões com pelo menos um campo não vazio
     */
    private List<CartaoCredito> filtrarCartoesPreenchidos(List<CartaoCredito> cartoes) {
        if (cartoes == null) return List.of();

        return cartoes.stream()
                .filter(c -> c != null && (
                        notBlank(c.getNumeroCartao()) ||
                                notBlank(c.getNomeImpresso()) ||
                                notBlank(c.getCodigoSeguranca()) ||
                                c.getBandeira() != null
                ))
                .collect(Collectors.toList());
    }

    /**
     * Filtra apenas endereços que são válidos para persistência.
     * Um endereço é considerado válido para persistir quando TODOS os
     * campos obrigatórios estão preenchidos.
     *
     * @param enderecos Lista de endereços
     * @return Lista de endereços completamente preenchidos
     */
    private List<Endereco> filtrarEnderecosValidos(List<Endereco> enderecos) {
        if (enderecos == null) return List.of();

        return enderecos.stream()
                .filter(this::isEnderecoValidoParaPersistir)
                .collect(Collectors.toList());
    }

    /**
     * Verifica se um endereço tem todos os campos obrigatórios preenchidos.
     *
     * @param e Endereço a ser verificado
     * @return true se todos os campos obrigatórios estão preenchidos
     */
    private boolean isEnderecoValidoParaPersistir(Endereco e) {
        if (e == null) return false;
        if (e.getTipoLogradouro() == null) return false;
        if (e.getUf() == null) return false;

        // Todos estes campos são obrigatórios e não podem ser vazios
        return notBlank(e.getRua()) &&
                notBlank(e.getNumero()) &&
                notBlank(e.getBairro()) &&
                notBlank(e.getCidade()) &&
                notBlank(e.getCep());
    }

    /**
     * Configura os relacionamentos bidirecionais entre Cliente e suas entidades filhas.
     * Também realiza limpeza de máscaras e validação de campos obrigatórios.
     *
     * @param cliente Cliente que terá seus relacionamentos configurados
     */
    private void configurarRelacionamentos(Cliente cliente) {
        // CONFIGURAÇÃO DE ENDEREÇOS
        // Cada endereço recebe a referência de volta para o cliente
        cliente.getEnderecos().forEach(e -> e.setCliente(cliente));

        // CONFIGURAÇÃO DE TELEFONES - COM VALIDAÇÃO RIGOROSA
        List<Telefone> telefonesValidos = new ArrayList<>();

        for (Telefone t : cliente.getTelefones()) {
            if (t == null) continue;

            // Limpeza de máscaras: remove tudo que não é dígito
            if (t.getDdd() != null) {
                t.setDdd(t.getDdd().replaceAll("[^0-9]", ""));
            }
            if (t.getNumero() != null) {
                t.setNumero(t.getNumero().replaceAll("[^0-9]", ""));
            }

            // Só adiciona se TODOS os campos obrigatórios estiverem preenchidos
            if (notBlank(t.getDdd()) && notBlank(t.getNumero()) && t.getTipo() != null) {
                t.setCliente(cliente);
                telefonesValidos.add(t);
                System.out.println("Telefone configurado: DDD=" + t.getDdd() +
                        ", Número=" + t.getNumero() +
                        ", Tipo=" + t.getTipo());
            } else {
                System.out.println("Telefone ignorado na configuração - campos incompletos: " +
                        "DDD=" + t.getDdd() +
                        ", Número=" + t.getNumero() +
                        ", Tipo=" + t.getTipo());
            }
        }

        // Substitui a lista original pela lista apenas com telefones válidos
        cliente.setTelefones(telefonesValidos);
        System.out.println("Telefones válidos após configuração: " + telefonesValidos.size());

        // CONFIGURAÇÃO DE CARTÕES
        List<CartaoCredito> cartoesValidos = new ArrayList<>();

        for (CartaoCredito cartao : cliente.getCartoesCredito()) {
            if (cartao == null) continue;

            // Limpeza de máscaras para campos numéricos
            if (cartao.getNumeroCartao() != null) {
                cartao.setNumeroCartao(cartao.getNumeroCartao().replaceAll("[^0-9]", ""));
            }
            if (cartao.getCodigoSeguranca() != null) {
                cartao.setCodigoSeguranca(cartao.getCodigoSeguranca().replaceAll("[^0-9]", ""));
            }

            // Validação de todos os campos obrigatórios
            if (notBlank(cartao.getNumeroCartao()) &&
                    notBlank(cartao.getNomeImpresso()) &&
                    notBlank(cartao.getCodigoSeguranca()) &&
                    cartao.getBandeira() != null) {

                cartao.setCliente(cliente);
                cartoesValidos.add(cartao);

                // Validação adicional específica para cartões de crédito
                cartaoCreditoService.validarCartao(cartao);
            } else {
                System.out.println("Cartão ignorado na configuração - campos incompletos");
            }
        }

        cliente.setCartoesCredito(cartoesValidos);
        System.out.println("Cartões válidos após configuração: " + cartoesValidos.size());
    }

    /**
     * Salva explicitamente os relacionamentos do cliente.
     * Este método é chamado após o cliente ser salvo, garantindo que
     * os endereços e telefones sejam persistidos mesmo com cascata configurada.
     *
     * @param cliente Cliente já salvo com ID gerado
     */
    private void salvarRelacionamentos(Cliente cliente) {
        // Só salva se o cliente tiver ID (já persistido)
        if (cliente.getId() != null) {
            if (!cliente.getEnderecos().isEmpty()) {
                // Salva todos os endereços em lote
                enderecoRepository.saveAll(cliente.getEnderecos());
                System.out.println("Endereços salvos: " + cliente.getEnderecos().size());
            }
            if (!cliente.getTelefones().isEmpty()) {
                // Salva todos os telefones em lote
                telefoneRepository.saveAll(cliente.getTelefones());
                System.out.println("Telefones salvos: " + cliente.getTelefones().size());
            }
        }
    }

    /**
     * Método utilitário para verificar se uma string não é nula nem vazia.
     *
     * @param s String a ser verificada
     * @return true se a string não é nula e não contém apenas espaços em branco
     */
    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * Valida se um CPF é válido segundo o algoritmo oficial brasileiro.
     *
     * Algoritmo de validação:
     * 1. Remove caracteres não numéricos
     * 2. Verifica se tem 11 dígitos
     * 3. Verifica se não são todos iguais (ex: 111.111.111-11)
     * 4. Calcula e verifica os dois dígitos verificadores
     *
     * @param cpf CPF a ser validado (pode estar com máscara)
     * @return true se o CPF é válido
     */
    private boolean validarCPF(String cpf) {
        // Remove todos os caracteres não numéricos (pontos, traços, espaços)
        cpf = cpf.replaceAll("[^0-9]", "");

        // Validação básica de tamanho
        if (cpf.length() != 11) return false;

        // Verifica se todos os dígitos são iguais (CPF inválido conhecido)
        if (cpf.matches("(\\d)\\1{10}")) return false;

        // CÁLCULO DO PRIMEIRO DÍGITO VERIFICADOR
        int soma = 0;
        for (int i = 0; i < 9; i++) {
            // Multiplica cada dígito por peso decrescente (10, 9, 8, ...)
            soma += Character.getNumericValue(cpf.charAt(i)) * (10 - i);
        }
        int resto = 11 - (soma % 11);
        int digitoVerificador1 = resto > 9 ? 0 : resto;

        // Verifica se o primeiro dígito verificador calculado é igual ao informado
        if (digitoVerificador1 != Character.getNumericValue(cpf.charAt(9))) return false;

        // CÁLCULO DO SEGUNDO DÍGITO VERIFICADOR
        soma = 0;
        for (int i = 0; i < 10; i++) {
            // Multiplica cada dígito por peso decrescente (11, 10, 9, ...)
            soma += Character.getNumericValue(cpf.charAt(i)) * (11 - i);
        }
        resto = 11 - (soma % 11);
        int digitoVerificador2 = resto > 9 ? 0 : resto;

        // Verifica se o segundo dígito verificador calculado é igual ao informado
        return digitoVerificador2 == Character.getNumericValue(cpf.charAt(10));
    }

    // ==================== MÉTODOS DE PESQUISA AVANÇADA ====================

    /**
     * Pesquisa clientes com múltiplos filtros (versão completa).
     * Permite combinar vários critérios de busca em uma única consulta.
     *
     * @param id Filtro por ID (busca exata)
     * @param nome Filtro por nome (busca parcial, case-insensitive)
     * @param email Filtro por email (busca parcial, case-insensitive)
     * @param cpf Filtro por CPF (busca exata)
     * @param genero Filtro por gênero (MASCULINO, FEMININO, OUTRO)
     * @param dataNascimento Filtro por data de nascimento (formato DD/MM/AAAA)
     * @param telefone Filtro por telefone (busca parcial)
     * @param status Filtro por status (true para inativo, false para ativo)
     * @return Lista de clientes que atendem aos critérios
     */
    public List<Cliente> pesquisarClientes(
            String id,
            String nome,
            String email,
            String cpf,
            String genero,
            String dataNascimento,
            String telefone,
            String status) {

        // LOGS para depuração e acompanhamento
        System.out.println("=== PESQUISANDO CLIENTES (COMPLETO) ===");
        System.out.println("Filtros recebidos:");
        System.out.println("  id: " + id);
        System.out.println("  nome: " + nome);
        System.out.println("  email: " + email);
        System.out.println("  cpf: " + cpf);
        System.out.println("  genero: " + genero);
        System.out.println("  dataNascimento: " + dataNascimento);
        System.out.println("  telefone: " + telefone);
        System.out.println("  status: " + status);

        // VALIDAÇÃO E CONVERSÃO DOS FILTROS

        // Validação de gênero - só aceita se for um valor válido do enum
        String generoStr = null;
        if (genero != null && !genero.isEmpty()) {
            try {
                // Tenta converter para enum - se falhar, ignora o filtro
                Genero.valueOf(genero.toUpperCase());
                generoStr = genero.toUpperCase();
                System.out.println("  gênero válido: " + generoStr);
            } catch (IllegalArgumentException e) {
                System.out.println("  gênero inválido, ignorando filtro: " + genero);
                // Gênero inválido, ignora o filtro
            }
        }

        // Conversão de status para Boolean
        Boolean inativado = null;
        if (status != null && !status.isEmpty()) {
            inativado = Boolean.parseBoolean(status);
            System.out.println("  status convertido: " + (inativado ? "Inativo" : "Ativo"));
        }

        // Executa a consulta utilizando query nativa ou JPQL com múltiplos filtros
        List<Cliente> resultados = clienteRepository.findByFiltrosCompleto(
                id, nome, email, cpf, generoStr, dataNascimento, telefone, inativado);

        System.out.println("Resultados encontrados: " + resultados.size());

        return resultados;
    }

    /**
     * Pesquisa clientes com filtros básicos.
     * Método mantido para compatibilidade com código existente.
     *
     * @param nome Filtro por nome
     * @param email Filtro por email
     * @param cpf Filtro por CPF
     * @param genero Filtro por gênero
     * @param status Filtro por status
     * @return Lista de clientes que atendem aos critérios
     */
    public List<Cliente> pesquisarClientesBasico(
            String nome,
            String email,
            String cpf,
            String genero,
            String status) {

        // Delega para o método completo, passando null para os filtros não utilizados
        return pesquisarClientes(null, nome, email, cpf, genero, null, null, status);
    }

    // ==================== MÉTODOS DE EXCLUSÃO E INATIVAÇÃO ====================

    /**
     * Inativa um cliente (exclusão lógica).
     * O cliente permanece no banco de dados, mas marcado como inativo.
     * Esta abordagem mantém o histórico e permite reativação posterior.
     *
     * @param id Identificador do cliente a ser inativado
     * @throws ClienteNaoEncontradoException Se o cliente não existir
     */
    @Transactional
    public void inativar(Long id) {
        // Busca o cliente, lança exceção se não existir
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(id));

        // Marca como inativado
        cliente.setInativado(true);

        // Persiste a alteração
        clienteRepository.save(cliente);
        System.out.println("Cliente inativado: " + id);
    }

    /**
     * Reativa um cliente previamente inativado.
     *
     * @param id Identificador do cliente a ser reativado
     * @throws ClienteNaoEncontradoException Se o cliente não existir
     */
    @Transactional
    public void reativar(Long id) {
        // Busca o cliente, lança exceção se não existir
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(id));

        // Marca como ativo
        cliente.setInativado(false);

        // Persiste a alteração
        clienteRepository.save(cliente);
        System.out.println("Cliente reativado: " + id);
    }

    /**
     * Remove permanentemente um cliente do banco de dados (exclusão física).
     * (endereços, telefones, cartões) devido ao cascade definido no modelo.
     *
     * @param id Identificador do cliente a ser excluído permanentemente
     * @throws ClienteNaoEncontradoException Se o cliente não existir
     */
    @Transactional
    public void excluirPermanentemente(Long id) {
        System.out.println("=== EXCLUINDO CLIENTE PERMANENTEMENTE ===");
        System.out.println("ID: " + id);

        // Verificar se o cliente existe
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException(id));

        // Log das entidades relacionadas que serão deletadas em cascata
        System.out.println("Cliente encontrado: " + cliente.getNome());
        System.out.println("Endereços relacionados: " + cliente.getEnderecos().size());
        System.out.println("Telefones relacionados: " + cliente.getTelefones().size());
        System.out.println("Cartões relacionados: " + cliente.getCartoesCredito().size());

        // Deleta o cliente (os relacionamentos serão deletados em cascata)
        clienteRepository.delete(cliente);

        System.out.println("Cliente excluído permanentemente do banco: " + id);
    }

    /**
     * Método mantido para compatibilidade com código existente.
     * Agora apenas redireciona para inativar (exclusão lógica).
     *
     * @param id Identificador do cliente
     */
    @Transactional
    public void excluir(Long id) {
        inativar(id);
    }

    /**
     * Método mantido para compatibilidade com código existente.
     * Agora apenas redireciona para excluirPermanentemente (exclusão física).
     *
     * @param id Identificador do cliente
     */
    @Transactional
    public void excluirFisicamente(Long id) {
        excluirPermanentemente(id);
    }
}