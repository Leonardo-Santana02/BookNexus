package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.cliente.CartaoCredito;
import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.cliente.Telefone;
import br.com.java.e_commerce.nexus.model.enums.BandeiraCartao;
import br.com.java.e_commerce.nexus.model.enums.Genero;
import br.com.java.e_commerce.nexus.model.enums.TipoLogradouro;
import br.com.java.e_commerce.nexus.model.enums.UF;
import br.com.java.e_commerce.nexus.service.cliente.CartaoCreditoService;
import br.com.java.e_commerce.nexus.service.cliente.ClienteService;
import br.com.java.e_commerce.nexus.service.exception.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/admin/clientes")
public class ClienteController {

    // ==================== ATRIBUTOS ====================

    /** Serviço que contém toda a lógica de negócio para clientes */
    private final ClienteService clienteService;

    /** Serviço especializado para operações com cartões de crédito */
    private final CartaoCreditoService cartaoCreditoService;

    /**
     * Construtor para injeção de dependências.
     * O Spring injeta automaticamente as implementações dos serviços.
     *
     * @param clienteService Serviço de cliente
     * @param cartaoCreditoService Serviço de cartão de crédito
     */
    public ClienteController(ClienteService clienteService,
                             CartaoCreditoService cartaoCreditoService) {
        this.clienteService = clienteService;
        this.cartaoCreditoService = cartaoCreditoService;
    }

    // ==================== MÉTODOS PARA VIEWS ADMIN ====================
    // Estes métodos retornam nomes de templates Thymeleaf (views)
    // que serão renderizados e enviados ao navegador.

    /**
     * Lista todos os clientes (ativos e inativos) para a página administrativa.
     *
     * Endpoint: GET /admin/clientes
     * View: admin/clientes/lista-clientes.html
     *
     * @param model Modelo do Spring MVC para passar atributos para a view
     * @return Nome do template Thymeleaf a ser renderizado
     */
    @GetMapping // Mapeia requisições GET para o caminho base
    public String listar(Model model) {
        // Busca todos os clientes
        List<Cliente> clientes = clienteService.listarTodos();

        // Adiciona atributos ao modelo para uso na view
        model.addAttribute("clientes", clientes);
        model.addAttribute("filtro", "todos"); // Indica qual filtro está ativo

        // Adiciona estatísticas para os cards da dashboard
        model.addAttribute("totalClientes", clientes.size());
        model.addAttribute("totalAtivos", clienteService.listarAtivos().size());
        model.addAttribute("totalInativos", clienteService.listarInativos().size());

        // Adiciona os enums para preencher os selects do formulário
        adicionarEnumsAoModel(model);

        return "admin/clientes/lista-clientes"; // Caminho do template
    }

    /**
     * Lista apenas clientes ativos (não inativados).
     *
     * Endpoint: GET /admin/clientes/ativos
     *
     * @param model Modelo do Spring MVC
     * @return Nome do template com a lista filtrada
     */
    @GetMapping("/ativos")
    public String listarAtivos(Model model) {
        List<Cliente> clientes = clienteService.listarAtivos();
        model.addAttribute("clientes", clientes);
        model.addAttribute("filtro", "ativos");

        // Estatísticas mantidas para consistência visual
        model.addAttribute("totalClientes", clienteService.listarTodos().size());
        model.addAttribute("totalAtivos", clientes.size());
        model.addAttribute("totalInativos", clienteService.listarInativos().size());

        adicionarEnumsAoModel(model);
        return "admin/clientes/lista-clientes";
    }

    /**
     * Lista apenas clientes inativados (exclusão lógica).
     *
     * Endpoint: GET /admin/clientes/inativos
     *
     * @param model Modelo do Spring MVC
     * @return Nome do template com a lista de inativos
     */
    @GetMapping("/inativos")
    public String listarInativos(Model model) {
        List<Cliente> clientes = clienteService.listarInativos();
        model.addAttribute("clientes", clientes);
        model.addAttribute("filtro", "inativos");

        model.addAttribute("totalClientes", clienteService.listarTodos().size());
        model.addAttribute("totalAtivos", clienteService.listarAtivos().size());
        model.addAttribute("totalInativos", clientes.size());

        adicionarEnumsAoModel(model);
        return "admin/clientes/lista-clientes";
    }

    /**
     * Exibe o formulário para cadastro de um novo cliente.
     *
     * Endpoint: GET /admin/clientes/novo
     *
     * Características importantes:
     * - Cria um novo objeto Cliente com estruturas iniciais (endereço, telefone, cartão)
     * - Se houver um cliente no modelo (vindo de erro de validação), mantém os dados
     * - Inicializa o cartão como preferencial (regra de negócio)
     *
     * @param model Modelo do Spring MVC
     * @return Nome do template de cadastro
     */
    @GetMapping("/novo")
    public String novoCliente(Model model) {
        // Verifica se já existe um cliente no modelo (caso venha de um erro de validação)
        if (!model.containsAttribute("cliente")) {
            Cliente cliente = new Cliente();

            // Adiciona um endereço inicial vazio para o formulário
            Endereco endereco = new Endereco();
            endereco.setCliente(cliente);
            cliente.getEnderecos().add(endereco);

            // Adiciona um telefone inicial vazio para o formulário
            Telefone telefone = new Telefone();
            telefone.setCliente(cliente);
            cliente.getTelefones().add(telefone);

            // Adiciona um cartão inicial como preferencial (regra: todo cliente deve ter um cartão preferencial)
            CartaoCredito cartao = new CartaoCredito();
            cartao.setCliente(cliente);
            cartao.setPreferencial(true); // Primeiro cartão é marcado como preferencial
            cliente.getCartoesCredito().add(cartao);

            // Inicializa campo senha vazio (evita NullPointerException no formulário)
            cliente.setSenha("");

            model.addAttribute("cliente", cliente);
        }

        model.addAttribute("isEdicao", false); // Indica que é um novo cadastro
        adicionarEnumsAoModel(model);

        return "admin/clientes/novo-cliente";
    }

    /**
     * Exibe o formulário para edição de um cliente existente.
     *
     * Endpoint: GET /admin/clientes/editar/{id}
     *
     * Características importantes:
     * - Não carrega a senha existente por questões de segurança
     * - Utiliza RedirectAttributes para mensagens flash (que persistem após redirect)
     * - Trata o caso de cliente não encontrado com redirect para lista
     *
     * @param id ID do cliente a ser editado
     * @param model Modelo do Spring MVC
     * @param redirectAttributes Atributos para mensagens flash após redirect
     * @return Nome do template de edição ou redirect em caso de erro
     */
    @GetMapping("/editar/{id}")
    public String editarCliente(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Verifica se já existe um cliente no modelo (vindo de erro de validação)
            Cliente cliente;
            if (model.containsAttribute("cliente")) {
                cliente = (Cliente) model.getAttribute("cliente");
            } else {
                // Busca o cliente pelo ID, lança exceção se não encontrar
                cliente = clienteService.buscarPorId(id)
                        .orElseThrow(() -> new ClienteNaoEncontradoException(id));
            }

            // SEGURANÇA: Não enviar a senha existente para o frontend
            // O campo senha fica vazio e o backend mantém a original
            cliente.setSenha("");

            model.addAttribute("cliente", cliente);
            model.addAttribute("isEdicao", true); // Indica que é uma edição
            adicionarEnumsAoModel(model);

            return "admin/clientes/editar-cliente";

        } catch (ClienteNaoEncontradoException e) {
            // Mensagem flash que sobrevive ao redirect
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/admin/clientes";
        }
    }

    /**
     * Exibe os detalhes completos de um cliente.
     *
     * Endpoint: GET /admin/clientes/detalhes/{id}
     *
     * Carrega o cliente com todos os relacionamentos (endereços, telefones, cartões)
     * utilizando o método buscarClienteCompleto() do service.
     *
     * @param id ID do cliente
     * @param model Modelo do Spring MVC
     * @param redirectAttributes Atributos para mensagens flash
     * @return Nome do template de detalhes ou redirect em caso de erro
     */
    @GetMapping("/detalhes/{id}")
    public String detalhesCliente(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Busca cliente com todos os relacionamentos carregados
            Cliente cliente = clienteService.buscarClienteCompleto(id);
            model.addAttribute("cliente", cliente);
            return "admin/clientes/detalhes-cliente";
        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/admin/clientes";
        }
    }

    // ==================== MÉTODOS DE AÇÃO (POST) ====================
    // Estes métodos processam dados enviados via POST (formulários)

    /**
     * Salva ou atualiza um cliente.
     *
     * Endpoint: POST /admin/clientes/salvar
     *
     * Este método é o coração da operação de CRUD, processando tanto cadastro
     * quanto edição de clientes. Características importantes:
     *
     * 1. Valida a confirmação de senha
     * 2. Associa entidades filhas ao cliente
     * 3. Valida regras de negócio (endereços de entrega/cobrança)
     * 4. Trata múltiplos tipos de exceção com preservação dos dados
     * 5. Em caso de erro, retorna para o formulário com dados preservados
     *
     * @param cliente Cliente com dados do formulário (binding automático)
     * @param confirmarSenha Campo de confirmação de senha
     * @param redirectAttributes Para mensagens flash em caso de sucesso
     * @param model Para preservar dados em caso de erro
     * @return Nome do template ou redirect
     */
    @PostMapping("/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente,
                                @RequestParam(required = false) String confirmarSenha,
                                RedirectAttributes redirectAttributes,
                                Model model) {

        try {
            // LOGS para debug - ajudam a identificar problemas
            System.out.println("=== SALVANDO CLIENTE ===");
            System.out.println("ID: " + cliente.getId());
            System.out.println("Nome: " + cliente.getNome());
            System.out.println("CPF: " + cliente.getCpf());
            System.out.println("Email: " + cliente.getEmail());
            System.out.println("Endereços: " + (cliente.getEnderecos() != null ? cliente.getEnderecos().size() : 0));
            System.out.println("Telefones: " + (cliente.getTelefones() != null ? cliente.getTelefones().size() : 0));
            System.out.println("Cartões: " + (cliente.getCartoesCredito() != null ? cliente.getCartoesCredito().size() : 0));

            // ========== VALIDAÇÃO DE SENHA ==========

            // Para novos clientes (ID nulo): senha é obrigatória
            if (cliente.getId() == null) {
                if (cliente.getSenha() == null || cliente.getSenha().trim().isEmpty()) {
                    throw new ValidacaoException("Senha é obrigatória");
                }
                // Verifica se senha e confirmação são iguais
                if (!cliente.getSenha().equals(confirmarSenha)) {
                    throw new ValidacaoException("Senha e confirmação de senha não conferem");
                }
            } else {
                // Para edição: se senha foi preenchida, validar confirmação
                if (cliente.getSenha() != null && !cliente.getSenha().isEmpty()) {
                    if (!cliente.getSenha().equals(confirmarSenha)) {
                        throw new ValidacaoException("Senha e confirmação de senha não conferem");
                    }
                } else {
                    // Se senha não foi preenchida, manter a existente
                    cliente.setSenha(null);
                }
            }

            // ========== ASSOCIAÇÃO DAS ENTIDADES FILHO ==========
            // Garante o relacionamento bidirecional: cada filho sabe quem é seu pai

            if (cliente.getEnderecos() != null) {
                cliente.getEnderecos().forEach(e -> e.setCliente(cliente));
            }
            if (cliente.getTelefones() != null) {
                cliente.getTelefones().forEach(t -> t.setCliente(cliente));
            }
            if (cliente.getCartoesCredito() != null) {
                cliente.getCartoesCredito().forEach(c -> c.setCliente(cliente));
            }

            // ========== VALIDAÇÃO DE ENDEREÇOS ==========
            // Regra de negócio: pelo menos um endereço de entrega e um de cobrança
            if (!validarEnderecos(cliente)) {
                throw new ValidacaoException("É necessário ter pelo menos um endereço de entrega e um de cobrança");
            }

            // ========== PERSISTÊNCIA ==========
            if (cliente.getId() == null) {
                // Novo cliente
                clienteService.salvar(cliente);
                redirectAttributes.addFlashAttribute("mensagem", "Cliente cadastrado com sucesso!");
            } else {
                // Atualização de cliente existente
                clienteService.salvarExistente(cliente);
                redirectAttributes.addFlashAttribute("mensagem", "Cliente atualizado com sucesso!");
            }

            return "redirect:/admin/clientes";

        } catch (CpfJaCadastradoException | EmailJaCadastradoException | ValidacaoException e) {
            // EXCEÇÕES DE NEGÓCIO: retorna para o formulário com os dados preservados
            // Isso é importante para não perder o que o usuário já digitou

            model.addAttribute("cliente", cliente);
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("isEdicao", cliente.getId() != null);
            adicionarEnumsAoModel(model);

            // Retorna para a página correta baseado no tipo de operação
            if (cliente.getId() == null) {
                return "admin/clientes/novo-cliente";
            } else {
                return "admin/clientes/editar-cliente";
            }

        } catch (DataIntegrityViolationException e) {
            // EXCEÇÕES DE INTEGRIDADE: violação de constraints do banco
            // (ex: CPF duplicado via constraint única)

            String erro = tratarErroConstraint(e);
            model.addAttribute("cliente", cliente);
            model.addAttribute("erro", erro);
            model.addAttribute("isEdicao", cliente.getId() != null);
            adicionarEnumsAoModel(model);

            if (cliente.getId() == null) {
                return "admin/clientes/novo-cliente";
            } else {
                return "admin/clientes/editar-cliente";
            }

        } catch (Exception e) {
            // EXCEÇÕES INESPERADAS: captura geral para não expor detalhes internos
            e.printStackTrace(); // Log do erro para debug

            model.addAttribute("cliente", cliente);
            model.addAttribute("erro", "Erro inesperado: " + e.getMessage());
            model.addAttribute("isEdicao", cliente.getId() != null);
            adicionarEnumsAoModel(model);

            if (cliente.getId() == null) {
                return "admin/clientes/novo-cliente";
            } else {
                return "admin/clientes/editar-cliente";
            }
        }
    }

    /**
     * Inativa um cliente (exclusão lógica).
     *
     * Endpoint: POST /admin/clientes/inativar/{id}
     *
     * O cliente continua no banco de dados mas marcado como inativo.
     * Esta operação pode ser revertida com reativar().
     *
     * @param id ID do cliente a ser inativado
     * @param redirectAttributes Para mensagens flash
     * @return Redirect para a lista de clientes
     */
    @PostMapping("/inativar/{id}")
    public String inativarCliente(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clienteService.inativar(id);
            redirectAttributes.addFlashAttribute("mensagem", "Cliente inativado com sucesso!");
        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao inativar cliente: " + e.getMessage());
        }
        return "redirect:/admin/clientes";
    }

    /**
     * Reativa um cliente previamente inativado.
     *
     * Endpoint: POST /admin/clientes/reativar/{id}
     *
     * @param id ID do cliente a ser reativado
     * @param redirectAttributes Para mensagens flash
     * @return Redirect para a lista de clientes
     */
    @PostMapping("/reativar/{id}")
    public String reativarCliente(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clienteService.reativar(id);
            redirectAttributes.addFlashAttribute("mensagem", "Cliente reativado com sucesso!");
        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao reativar cliente: " + e.getMessage());
        }
        return "redirect:/admin/clientes";
    }

    /**
     * Remove permanentemente um cliente do banco de dados (exclusão física).
     *
     * Endpoint: POST /admin/clientes/excluir-permanentemente/{id}
     *
     * ATENÇÃO: Esta operação é irreversível! Os dados não podem ser recuperados.
     * Use com cautela.
     *
     * @param id ID do cliente a ser excluído permanentemente
     * @param redirectAttributes Para mensagens flash
     * @return Redirect para a lista de clientes
     */
    @PostMapping("/excluir-permanentemente/{id}")
    public String excluirClientePermanentemente(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clienteService.excluirPermanentemente(id);
            redirectAttributes.addFlashAttribute("mensagem", "Cliente excluído permanentemente com sucesso!");
        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao excluir cliente permanentemente: " + e.getMessage());
        }
        return "redirect:/admin/clientes";
    }

    /**
     * Método mantido para compatibilidade - agora redireciona para inativar.
     *
     * Endpoint: POST /admin/clientes/excluir/{id}
     *
     * @param id ID do cliente
     * @param redirectAttributes Para mensagens flash
     * @return Redirect para inativarCliente
     */
    @PostMapping("/excluir/{id}")
    public String excluirCliente(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return inativarCliente(id, redirectAttributes);
    }

    // ==================== MÉTODOS PARA PESQUISA (AJAX) ====================
    // Estes métodos retornam dados em JSON para requisições AJAX do frontend

    /**
     * Pesquisa clientes com múltiplos filtros.
     *
     * Endpoint: GET /admin/clientes/pesquisar
     *
     * Todos os parâmetros são opcionais. Os filtros são combinados com AND.
     *
     * @param id Filtro por ID (exato)
     * @param nome Filtro por nome (parcial)
     * @param email Filtro por email (parcial)
     * @param cpf Filtro por CPF (exato)
     * @param genero Filtro por gênero
     * @param dataNascimento Filtro por data de nascimento
     * @param telefone Filtro por telefone (parcial)
     * @param status Filtro por status (true=inativo, false=ativo)
     * @return Lista de clientes em formato JSON
     */
    @GetMapping("/pesquisar")
    @ResponseBody // Indica que o retorno deve ser serializado para JSON (não é uma view)
    public List<Cliente> pesquisarClientes(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String genero,
            @RequestParam(required = false) String dataNascimento,
            @RequestParam(required = false) String telefone,
            @RequestParam(required = false) String status) {

        return clienteService.pesquisarClientes(id, nome, email, cpf, genero,
                dataNascimento, telefone, status);
    }

    /**
     * Método simplificado para busca por nome.
     * Utilizado em campos de autocomplete no frontend.
     *
     * Endpoint: GET /admin/clientes/buscar-por-nome?termo=xxx
     *
     * @param termo Texto para busca no nome do cliente
     * @return Lista de clientes que contêm o termo no nome
     */
    @GetMapping("/buscar-por-nome")
    @ResponseBody
    public List<Cliente> buscarPorNome(@RequestParam String termo) {
        return clienteService.buscarPorNome(termo);
    }

    // ==================== MÉTODOS AUXILIARES ====================

    /**
     * Adiciona todos os enums utilizados nos formulários ao modelo.
     *
     * Estes enums são usados para preencher selects e radios nos templates:
     * - Tipos de logradouro (Rua, Avenida, etc.)
     * - Unidades Federativas (UF)
     * - Bandeiras de cartão (Visa, Mastercard, etc.)
     * - Gêneros (Masculino, Feminino, Outro)
     *
     * @param model Modelo do Spring MVC
     */
    private void adicionarEnumsAoModel(Model model) {
        model.addAttribute("tiposLogradouro", TipoLogradouro.values());
        model.addAttribute("ufs", UF.values());
        model.addAttribute("bandeiras", BandeiraCartao.values());
        model.addAttribute("generos", Genero.values());
    }

    /**
     * Trata exceções de violação de constraint do banco de dados.
     *
     * Analisa o nome da constraint violada e retorna uma mensagem amigável.
     *
     * @param e DataIntegrityViolationException lançada pelo banco
     * @return Mensagem de erro amigável para o usuário
     */
    private String tratarErroConstraint(DataIntegrityViolationException e) {
        // Verifica se a causa é uma violação de constraint do Hibernate
        if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            if (constraintName != null) {
                // Verifica qual constraint foi violada baseado no nome
                if (constraintName.toLowerCase().contains("cpf")) {
                    return "CPF já cadastrado!";
                } else if (constraintName.toLowerCase().contains("email")) {
                    return "E-mail já cadastrado!";
                }
            }
        }
        return "Erro de integridade de dados. Verifique se CPF ou E-mail já estão cadastrados.";
    }

    /**
     * Valida se o cliente possui endereços de entrega e cobrança.
     *
     * Regra de negócio: todo cliente deve ter pelo menos um endereço
     * marcado como entrega e um marcado como cobrança.
     *
     * @param cliente Cliente a ser validado
     * @return true se a validação passar, false caso contrário
     */
    private boolean validarEnderecos(Cliente cliente) {
        if (cliente.getEnderecos() == null || cliente.getEnderecos().isEmpty()) {
            return false;
        }

        // Verifica existência de endereço de entrega
        boolean temEntrega = cliente.getEnderecos().stream()
                .anyMatch(Endereco::isEnderecoEntrega);

        // Verifica existência de endereço de cobrança
        boolean temCobranca = cliente.getEnderecos().stream()
                .anyMatch(Endereco::isEnderecoCobranca);

        return temEntrega && temCobranca;
    }

    // ==================== MÉTODOS PARA MANIPULAÇÃO DE CARTÕES ====================

    /**
     * Lista os cartões de um cliente com dados mascarados (segurança).
     *
     * Endpoint: GET /admin/clientes/{clienteId}/cartoes
     *
     * Os números dos cartões são mascarados para exibição, mostrando apenas
     * os últimos 4 dígitos (ex: **** **** **** 1234).
     *
     * @param clienteId ID do cliente
     * @return Lista de cartões com dados mascarados em JSON
     */
    @GetMapping("/{clienteId}/cartoes")
    @ResponseBody
    public List<CartaoCredito> listarCartoesCliente(@PathVariable Long clienteId) {
        return cartaoCreditoService.buscarPorClienteMascarados(clienteId);
    }

    /**
     * Define um cartão como preferencial para o cliente.
     *
     * Endpoint: POST /admin/clientes/cartoes/{cartaoId}/preferencial
     *
     * Regra de negócio: apenas um cartão pode ser preferencial por cliente.
     * O service se encarrega de desmarcar o anterior.
     *
     * @param cartaoId ID do cartão a ser definido como preferencial
     * @return ResponseEntity com mensagem de sucesso ou erro
     */
    @PostMapping("/cartoes/{cartaoId}/preferencial")
    @ResponseBody
    public ResponseEntity<?> definirCartaoPreferencial(@PathVariable Long cartaoId) {
        try {
            CartaoCredito cartao = cartaoCreditoService.definirComoPreferencial(cartaoId);
            return ResponseEntity.ok(Map.of(
                    "mensagem", "Cartão definido como preferencial",
                    "cartaoId", cartao.getId()
            ));
        } catch (CartaoNaoEncontradoException | ValidacaoException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // ==================== MÉTODOS PARA API DO FRONTEND (PÚBLICO) ====================
    // Estes endpoints são consumidos por componentes JavaScript no frontend

    /**
     * Endpoint público para o seletor de clientes da home.
     *
     * Endpoint: GET /admin/clientes/api/para-seletor
     *
     * Retorna apenas clientes ATIVOS com campos básicos (id e nome) para
     * preencher selects, autocompletes e componentes de seleção.
     *
     * @return Lista de mapas com id e nome em JSON
     */
    @GetMapping("/api/para-seletor")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> listarClientesParaSeletor() {
        // Busca apenas clientes ativos
        List<Cliente> clientes = clienteService.listarAtivos();

        // Transforma a lista em um formato mais leve para o frontend
        List<Map<String, Object>> resultado = clientes.stream()
                .map(cliente -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cliente.getId());
                    map.put("nome", cliente.getNome());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(resultado);
    }

    /**
     * Endpoint alternativo que retorna todos os clientes (incluindo inativos).
     *
     * Endpoint: GET /admin/clientes/api/todos
     *
     * Útil para testes, relatórios ou administração.
     *
     * @return Lista de todos os clientes com id, nome e status
     */
    @GetMapping("/api/todos")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> listarTodosClientes() {
        List<Cliente> clientes = clienteService.listarTodos();

        List<Map<String, Object>> resultado = clientes.stream()
                .map(cliente -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cliente.getId());
                    map.put("nome", cliente.getNome());
                    map.put("inativado", cliente.getInativado()); // Inclui status
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(resultado);
    }

    /**
     * Endpoint para selecionar um cliente e armazenar na sessão.
     *
     * Endpoint: POST /admin/clientes/api/{id}/selecionar
     *
     * Pode ser usado para persistir o cliente selecionado na sessão do usuário.
     *
     * @param id ID do cliente a ser selecionado
     * @return ResponseEntity com dados do cliente ou mensagem de erro
     */
    @PostMapping("/api/{id}/selecionar")
    @ResponseBody
    public ResponseEntity<?> selecionarCliente(@PathVariable Long id) {
        try {
            Cliente cliente = clienteService.buscarPorId(id)
                    .orElseThrow(() -> new ClienteNaoEncontradoException(id));

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("id", cliente.getId());
            resultado.put("nome", cliente.getNome());
            resultado.put("mensagem", "Cliente selecionado com sucesso");

            return ResponseEntity.ok(resultado);

        } catch (ClienteNaoEncontradoException e) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", e.getMessage());
            return ResponseEntity.badRequest().body(erro);
        }
    }

    // ==================== MÉTODOS DA ÁREA DO CLIENTE ====================
    // Estes endpoints são para a área do cliente logado (frontend público)

    /**
     * Exibe a página de pedidos do cliente.
     *
     * Endpoint: GET /admin/clientes/minha-conta/pedidos
     *
     * TODO: Implementar lógica para obter o cliente logado via SecurityContext
     *
     * @param model Modelo do Spring MVC
     * @return Nome do template da área do cliente
     */
    @GetMapping("/minha-conta/pedidos")
    public String pedidosCliente(Model model) {
        // TODO: Implementar lógica para cliente logado
        // Cliente cliente = obterClienteLogado();
        // List<Pedido> pedidos = pedidoService.buscarPorCliente(cliente.getId());
        // model.addAttribute("pedidos", pedidos);
        return "cliente/pedidos";
    }

    /**
     * Exibe a página de solicitação de troca.
     *
     * Endpoint: GET /admin/clientes/solicitar-troca
     *
     * @return Nome do template de solicitação de troca
     */
    @GetMapping("/solicitar-troca")
    public String solicitarTrocaCliente() {
        return "cliente/solicitar-troca";
    }
}
