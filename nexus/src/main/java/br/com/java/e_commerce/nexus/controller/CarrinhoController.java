package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.enums.TipoLogradouro;
import br.com.java.e_commerce.nexus.model.enums.UF;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.cliente.EnderecoRepository;
import br.com.java.e_commerce.nexus.service.carrinho.CarrinhoService;
import br.com.java.e_commerce.nexus.service.exception.ClienteNaoEncontradoException;
import br.com.java.e_commerce.nexus.service.exception.ValidacaoException;
import br.com.java.e_commerce.nexus.service.venda.CupomService;
import br.com.java.e_commerce.nexus.service.venda.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador responsável por gerenciar todas as operações relacionadas ao carrinho de compras.
 *
 * Este controlador suporta dois tipos de requisição:
 * 1. Requisições síncronas (renderização de páginas HTML) - métodos que retornam String
 * 2. Requisições assíncronas (AJAX/REST) - métodos anotados com @ResponseBody
 *
 * @Controller: Indica que esta classe é um controlador Spring MVC
 * @RequestMapping("/carrinho"): Todas as URLs deste controlador começam com "/carrinho"
 *
 * URLs disponíveis:
 * - GET  /carrinho                    → Página do carrinho
 * - POST /carrinho/adicionar          → Adicionar produto
 * - POST /carrinho/aplicar-cupom      → Aplicar cupom
 * - POST /carrinho/limpar             → Limpar carrinho
 * - GET  /carrinho/remover/{id}       → Remover produto
 * - POST /carrinho/criar-pedido       → Criar pedido e ir para pagamento
 * - POST /carrinho/limpar-carrinho-cliente → Limpar carrinho via AJAX
 *
 * Endpoints AJAX:
 * - POST /carrinho/api/alterar-quantidade  → Alterar quantidade de item
 * - POST /carrinho/api/remover             → Remover item
 * - POST /carrinho/api/remover-cupom       → Remover cupom
 * - GET  /carrinho/api/resumo              → Obter resumo do carrinho
 * - POST /carrinho/api/calcular-frete      → Calcular frete por endereço
 * - POST /carrinho/api/aplicar-cupom       → Aplicar cupom (AJAX)
 * - POST /carrinho/api/adicionar-endereco  → Adicionar endereço via AJAX
 */
@Controller
@RequestMapping("/carrinho")
public class CarrinhoController {

    // Serviços e repositórios injetados
    private final CarrinhoService carrinhoService;   // Operações do carrinho
    private final CupomService cupomService;         // Busca de cupons disponíveis
    private final PedidoService pedidoService;       // Criação de pedidos
    private final EnderecoRepository enderecoRepository;     // Persistência de endereços
    private final ClienteRepository clienteRepository;       // Busca de clientes

    /**
     * Construtor para injeção de dependências.
     * Spring injeta automaticamente todas as dependências.
     *
     * @param carrinhoService Serviço de operações do carrinho
     * @param cupomService Serviço de consulta de cupons
     * @param pedidoService Serviço de criação de pedidos
     * @param enderecoRepository Repositório de endereços
     * @param clienteRepository Repositório de clientes
     */
    public CarrinhoController(CarrinhoService carrinhoService,
                              CupomService cupomService,
                              PedidoService pedidoService,
                              EnderecoRepository enderecoRepository,
                              ClienteRepository clienteRepository) {
        this.carrinhoService = carrinhoService;
        this.cupomService = cupomService;
        this.pedidoService = pedidoService;
        this.enderecoRepository = enderecoRepository;
        this.clienteRepository = clienteRepository;
    }

    // ===== MÉTODOS AUXILIARES =====

    /**
     * Obtém o ID do cliente a partir da requisição.
     *
     * Estratégia de obtenção (em ordem de prioridade):
     * 1. Parâmetro "clienteId" na URL/query string
     * 2. Atributo de sessão "clienteAtualCarrinho"
     * 3. Valor padrão 1L (fallback para desenvolvimento/demo)
     *
     * @param request Objeto HttpServletRequest para acessar parâmetros e sessão
     * @return ID do cliente autenticado (ou padrão)
     */
    private Long getClienteId(HttpServletRequest request) {
        Long clienteId = null;

        // ===== TENTATIVA 1: Parâmetro da requisição =====
        String clienteIdParam = request.getParameter("clienteId");
        if (clienteIdParam != null && !clienteIdParam.isEmpty()) {
            try {
                clienteId = Long.parseLong(clienteIdParam);
                // Armazena na sessão para próximas requisições
                request.getSession().setAttribute("clienteAtualCarrinho", clienteId);
                return clienteId;
            } catch (NumberFormatException e) {
                // Ignora erro de parsing e continua para próxima tentativa
            }
        }

        // ===== TENTATIVA 2: Atributo de sessão =====
        clienteId = (Long) request.getSession().getAttribute("clienteAtualCarrinho");
        if (clienteId != null) {
            return clienteId;
        }

        // ===== TENTATIVA 3: Fallback padrão =====
        // NOTA: Em produção, isso deveria ser substituído por autenticação real
        // Ex: SecurityContextHolder.getContext().getAuthentication().getPrincipal()
        return 1L;
    }

    /**
     * Monta um mapa com o resumo financeiro do carrinho.
     * Útil para respostas AJAX onde o front-end precisa atualizar valores.
     *
     * @param carrinho Carrinho a ser resumido
     * @return Mapa com subtotal, desconto, total, quantidade e frete
     */
    private Map<String, Object> montarResumo(Carrinho carrinho) {
        Map<String, Object> response = new HashMap<>();
        response.put("subtotal", carrinho.getSubtotal());           // Soma dos produtos
        response.put("desconto", carrinho.getDescontoTotal());      // Descontos aplicados
        response.put("total", carrinho.getTotal());                 // Valor final (subtotal + frete - desconto)
        response.put("quantidadeItens", carrinho.getQuantidadeItens()); // Número total de itens
        response.put("valorFrete", carrinho.getValorFrete());       // Valor do frete
        return response;
    }

    // ===== PÁGINA DO CARRINHO =====

    /**
     * Exibe a página principal do carrinho de compras.
     *
     * @param model Model do Spring para passar atributos para a view
     * @param request Requisição HTTP para obter ID do cliente
     * @return Nome da template Thymeleaf/JSP a ser renderizada
     */
    @GetMapping
    public String verCarrinho(Model model, HttpServletRequest request) {
        Long clienteId = getClienteId(request);

        try {
            // Busca o carrinho completo (com itens carregados)
            Carrinho carrinho = carrinhoService.buscarComItens(clienteId);

            // Adiciona o carrinho e o cliente ao modelo da view
            model.addAttribute("carrinho", carrinho);
            model.addAttribute("cliente", carrinho.getCliente());

            // Adiciona listas de cupons disponíveis para o cliente
            // Cupons promocionais: descontos aplicáveis ao valor dos produtos
            model.addAttribute("cuponsPromocionais",
                    cupomService.buscarCuponsPromocionaisAtivosCliente(clienteId));

            // Cupons de troca: créditos que podem ser usados no pagamento
            model.addAttribute("cuponsTroca",
                    cupomService.buscarCuponsTrocaAtivosCliente(clienteId));

        } catch (Exception e) {
            // Em caso de erro, adiciona mensagem ao modelo
            model.addAttribute("erro", "Erro ao carregar carrinho: " + e.getMessage());
        }

        // Retorna o nome da view (assumindo Thymeleaf/JSP em templates/cliente/carrinho)
        return "cliente/carrinho";
    }

    // ===== NOVO ENDPOINT: CRIAR PEDIDO E REDIRECIONAR PARA PAGAMENTO =====

    /**
     * Cria um pedido a partir do carrinho atual e redireciona para a página de pagamento.
     *
     * Este é o endpoint principal do checkout. Ele:
     * 1. Valida o carrinho e o endereço
     * 2. Cria o pedido (com estoque baixado e valores congelados)
     * 3. Redireciona para o fluxo de pagamento
     *
     * @param enderecoId ID do endereço de entrega selecionado
     * @param request Requisição HTTP
     * @param redirectAttributes Para mensagens flash após redirecionamento
     * @return Redirecionamento para a página de pagamento do pedido
     */
    @PostMapping("/criar-pedido")
    public String criarPedidoEIrParaPagamento(@RequestParam Long enderecoId,
                                              HttpServletRequest request,
                                              RedirectAttributes redirectAttributes) {
        Long clienteId = getClienteId(request);

        try {
            // 1. Cria o pedido a partir do carrinho do cliente
            // Este método valida estoque, congela preços e dá baixa no estoque
            Pedido pedido = pedidoService.criarPedidoDoCarrinho(clienteId, enderecoId);

            // 2. Redireciona para a tela de pagamento do pedido criado
            // O PagamentoController deve ter um endpoint /pagamentos/pedido/{id}
            return "redirect:/pagamentos/pedido/" + pedido.getId();

        } catch (Exception e) {
            // Em caso de erro (estoque insuficiente, etc), volta para o carrinho com mensagem
            redirectAttributes.addFlashAttribute("erro", "Erro ao criar pedido: " + e.getMessage());
            return "redirect:/carrinho";
        }
    }

    // ===== NOVO ENDPOINT: LIMPAR CARRINHO DO CLIENTE =====

    /**
     * Limpa o carrinho do cliente via requisição AJAX.
     * Usado após a confirmação do pagamento para esvaziar o carrinho.
     *
     * @param request Requisição HTTP
     * @return ResponseEntity com status e mensagem de sucesso/erro
     */
    @PostMapping("/limpar-carrinho-cliente")
    @ResponseBody
    public ResponseEntity<?> limparCarrinhoCliente(HttpServletRequest request) {
        Long clienteId = getClienteId(request);

        try {
            carrinhoService.limparCarrinho(clienteId);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("sucesso", true);
            resposta.put("mensagem", "Carrinho limpo com sucesso!");
            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            Map<String, Object> erro = new HashMap<>();
            erro.put("sucesso", false);
            erro.put("mensagem", "Erro ao limpar carrinho: " + e.getMessage());
            return ResponseEntity.badRequest().body(erro);
        }
    }

    // ===== AÇÕES COM RETORNO DE PÁGINA (REDIRECT) =====

    /**
     * Adiciona um produto ao carrinho e redireciona para a página do carrinho.
     *
     * @param produtoId ID do produto a ser adicionado
     * @param quantidade Quantidade (padrão = 1)
     * @param redirectAttributes Para mensagens flash
     * @param request Requisição HTTP
     * @return Redirecionamento para /carrinho
     */
    @PostMapping("/adicionar")
    public String adicionarProduto(@RequestParam Long produtoId,
                                   @RequestParam(defaultValue = "1") int quantidade,
                                   RedirectAttributes redirectAttributes,
                                   HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            carrinhoService.adicionarItem(clienteId, produtoId, quantidade);
            redirectAttributes.addFlashAttribute("sucesso", "Produto adicionado ao carrinho!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    /**
     * Aplica um cupom ao carrinho e redireciona.
     *
     * @param codigoCupom Código do cupom a ser aplicado
     * @param redirectAttributes Para mensagens flash
     * @param request Requisição HTTP
     * @return Redirecionamento para /carrinho
     */
    @PostMapping("/aplicar-cupom")
    public String aplicarCupom(@RequestParam String codigoCupom,
                               RedirectAttributes redirectAttributes,
                               HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            carrinhoService.adicionarCupom(clienteId, codigoCupom);
            redirectAttributes.addFlashAttribute("sucesso", "Cupom aplicado com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    /**
     * Limpa completamente o carrinho (remove todos os itens e cupons).
     *
     * @param redirectAttributes Para mensagens flash
     * @param request Requisição HTTP
     * @return Redirecionamento para /carrinho
     */
    @PostMapping("/limpar")
    public String limparCarrinho(RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            carrinhoService.limparCarrinho(clienteId);
            redirectAttributes.addFlashAttribute("sucesso", "Carrinho limpo com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    /**
     * Remove um item do carrinho (versão com redirect).
     *
     * @param produtoId ID do produto a ser removido
     * @param redirectAttributes Para mensagens flash
     * @param request Requisição HTTP
     * @return Redirecionamento para /carrinho
     */
    @GetMapping("/remover/{produtoId}")
    public String removerItemView(@PathVariable Long produtoId,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            carrinhoService.removerItem(clienteId, produtoId);
            redirectAttributes.addFlashAttribute("sucesso", "Item removido do carrinho!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    // ===== API REST (AJAX) - RESPOSTAS EM JSON =====

    /**
     * Altera a quantidade de um item no carrinho via AJAX.
     *
     * @param produtoId ID do produto
     * @param quantidade Nova quantidade
     * @param request Requisição HTTP
     * @return ResponseEntity com o resumo atualizado do carrinho
     */
    @PostMapping("/api/alterar-quantidade")
    @ResponseBody
    public ResponseEntity<?> alterarQuantidade(@RequestParam Long produtoId,
                                               @RequestParam int quantidade,
                                               HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            Carrinho carrinho = carrinhoService.alterarQuantidadeItem(clienteId, produtoId, quantidade);
            return ResponseEntity.ok(montarResumo(carrinho));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Remove um item do carrinho via AJAX.
     *
     * @param produtoId ID do produto a ser removido
     * @param request Requisição HTTP
     * @return ResponseEntity com o resumo atualizado do carrinho
     */
    @PostMapping("/api/remover")
    @ResponseBody
    public ResponseEntity<?> removerItem(@RequestParam Long produtoId,
                                         HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            Carrinho carrinho = carrinhoService.removerItem(clienteId, produtoId);
            return ResponseEntity.ok(montarResumo(carrinho));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Remove um cupom do carrinho via AJAX.
     *
     * @param cupomId ID do cupom a ser removido
     * @param request Requisição HTTP
     * @return ResponseEntity com o novo desconto e total
     */
    @PostMapping("/api/remover-cupom")
    @ResponseBody
    public ResponseEntity<?> removerCupom(@RequestParam Long cupomId,
                                          HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            Carrinho carrinho = carrinhoService.removerCupom(clienteId, cupomId);

            Map<String, Object> response = new HashMap<>();
            response.put("desconto", carrinho.getDescontoTotal());
            response.put("total", carrinho.getTotal());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Obtém um resumo completo do carrinho via AJAX.
     *
     * @param request Requisição HTTP
     * @return ResponseEntity com resumo e lista de itens
     */
    @GetMapping("/api/resumo")
    @ResponseBody
    public ResponseEntity<?> getResumo(HttpServletRequest request) {

        Long clienteId = getClienteId(request);

        try {
            Carrinho carrinho = carrinhoService.buscarComItens(clienteId);

            Map<String, Object> response = montarResumo(carrinho);
            response.put("itens", carrinho.getItens());  // Adiciona a lista completa de itens

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // ===== NOVO ENDPOINT: CALCULAR FRETE POR ENDEREÇO =====

    /**
     * Calcula o frete com base no endereço selecionado via AJAX.
     *
     * @param enderecoId ID do endereço de entrega
     * @param request Requisição HTTP
     * @return ResponseEntity com o resumo atualizado incluindo o frete
     */
    @PostMapping("/api/calcular-frete")
    @ResponseBody
    public ResponseEntity<?> calcularFrete(@RequestParam Long enderecoId,
                                           HttpServletRequest request) {
        Long clienteId = getClienteId(request);

        try {
            Carrinho carrinho = carrinhoService.atualizarFretePorEndereco(clienteId, enderecoId);
            Map<String, Object> response = montarResumo(carrinho);
            response.put("sucesso", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // ===== ENDPOINT AJAX PARA APLICAR CUPOM (RETORNA JSON) =====

    /**
     * Aplica um cupom ao carrinho via AJAX.
     * Retorna dados detalhados para o front-end atualizar a interface.
     *
     * @param codigoCupom Código do cupom
     * @param request Requisição HTTP
     * @return ResponseEntity com resumo completo e lista de cupons aplicados
     */
    @PostMapping("/api/aplicar-cupom")
    @ResponseBody
    public ResponseEntity<?> aplicarCupomAjax(@RequestParam String codigoCupom,
                                              HttpServletRequest request) {
        Long clienteId = getClienteId(request);

        try {
            Carrinho carrinho = carrinhoService.adicionarCupom(clienteId, codigoCupom);

            // ===== DADOS FINANCEIROS =====
            Map<String, Object> response = new HashMap<>();
            response.put("subtotal", carrinho.getSubtotal());
            response.put("desconto", carrinho.getDescontoTotal());
            response.put("total", carrinho.getTotal());
            response.put("valorFrete", carrinho.getValorFrete());

            // ===== LISTA DE CUPONS APLICADOS =====
            // Converte a lista de Cupom para Map para serialização JSON
            List<Map<String, Object>> cuponsList = new ArrayList<>();
            for (Cupom cupom : carrinho.getCupons()) {
                Map<String, Object> cupomMap = new HashMap<>();
                cupomMap.put("id", cupom.getId());
                cupomMap.put("codigo", cupom.getCodigo());
                cupomMap.put("valor", cupom.getValor());
                cupomMap.put("tipo", cupom.getTipo().toString());
                cuponsList.add(cupomMap);
            }
            response.put("cupons", cuponsList);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // ===== NOVO ENDPOINT: ADICIONAR ENDEREÇO VIA AJAX (CARRINHO) =====

    /**
     * Adiciona um novo endereço para o cliente durante o fluxo de checkout.
     *
     * Este endpoint é chamado via AJAX quando o cliente preenche o formulário
     * de novo endereço diretamente na página do carrinho.
     *
     * @param requestData Mapa com os dados do endereço enviados no body da requisição
     * @param request Requisição HTTP para obter o cliente
     * @return ResponseEntity com o ID do endereço criado e sua descrição
     */
    @PostMapping("/api/adicionar-endereco")
    @ResponseBody
    public ResponseEntity<?> adicionarEndereco(@RequestBody Map<String, Object> requestData,
                                               HttpServletRequest request) {
        Long clienteId = getClienteId(request);

        try {
            // ===== 1. BUSCAR O CLIENTE =====
            var cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));

            // ===== 2. CRIAR E PREENCHER O ENDEREÇO =====
            Endereco endereco = new Endereco();

            // --- CAMPOS OBRIGATÓRIOS ---

            // Tipo de logradouro (Rua, Avenida, Praça, etc.)
            String tipoLogradouroStr = (String) requestData.get("tipoLogradouro");
            if (tipoLogradouroStr == null || tipoLogradouroStr.isEmpty()) {
                throw new ValidacaoException("Tipo de logradouro é obrigatório");
            }
            endereco.setTipoLogradouro(TipoLogradouro.valueOf(tipoLogradouroStr));

            // Nome da rua/avenida
            String rua = (String) requestData.get("rua");
            if (rua == null || rua.trim().isEmpty()) {
                throw new ValidacaoException("Logradouro é obrigatório");
            }
            endereco.setRua(rua);

            // Número do imóvel
            String numero = (String) requestData.get("numero");
            if (numero == null || numero.trim().isEmpty()) {
                throw new ValidacaoException("Número é obrigatório");
            }
            endereco.setNumero(numero);

            // Bairro
            String bairro = (String) requestData.get("bairro");
            if (bairro == null || bairro.trim().isEmpty()) {
                throw new ValidacaoException("Bairro é obrigatório");
            }
            endereco.setBairro(bairro);

            // Cidade
            String cidade = (String) requestData.get("cidade");
            if (cidade == null || cidade.trim().isEmpty()) {
                throw new ValidacaoException("Cidade é obrigatória");
            }
            endereco.setCidade(cidade);

            // UF (Unidade Federativa)
            String ufStr = (String) requestData.get("uf");
            if (ufStr == null || ufStr.trim().isEmpty()) {
                throw new ValidacaoException("UF é obrigatória");
            }
            endereco.setUf(UF.valueOf(ufStr.toUpperCase()));

            // CEP (apenas números)
            String cep = (String) requestData.get("cep");
            if (cep == null || cep.trim().isEmpty()) {
                throw new ValidacaoException("CEP é obrigatório");
            }
            endereco.setCep(cep.replaceAll("[^0-9]", "")); // Remove caracteres não numéricos

            // --- CAMPOS OPCIONAIS ---
            if (requestData.containsKey("complemento") && requestData.get("complemento") != null) {
                endereco.setComplemento((String) requestData.get("complemento"));
            }
            if (requestData.containsKey("observacoes") && requestData.get("observacoes") != null) {
                endereco.setObservacoes((String) requestData.get("observacoes"));
            }
            if (requestData.containsKey("apelido") && requestData.get("apelido") != null) {
                endereco.setApelido((String) requestData.get("apelido"));
            }

            // --- FLAGS ---
            // Endereço de entrega: sempre true para endereços cadastrados no carrinho
            endereco.setEnderecoEntrega(true);

            // Endereço de cobrança: pode ser o mesmo ou diferente
            endereco.setEnderecoCobranca(
                    requestData.containsKey("enderecoCobranca") &&
                            Boolean.TRUE.equals(requestData.get("enderecoCobranca"))
            );

            // ===== 3. VALIDAÇÕES DE TAMANHO MÁXIMO =====
            if (endereco.getRua() != null && endereco.getRua().length() > 100) {
                throw new ValidacaoException("Logradouro deve ter no máximo 100 caracteres");
            }
            if (endereco.getNumero() != null && endereco.getNumero().length() > 10) {
                throw new ValidacaoException("Número deve ter no máximo 10 caracteres");
            }
            if (endereco.getComplemento() != null && endereco.getComplemento().length() > 50) {
                throw new ValidacaoException("Complemento deve ter no máximo 50 caracteres");
            }
            if (endereco.getBairro() != null && endereco.getBairro().length() > 50) {
                throw new ValidacaoException("Bairro deve ter no máximo 50 caracteres");
            }
            if (endereco.getCidade() != null && endereco.getCidade().length() > 50) {
                throw new ValidacaoException("Cidade deve ter no máximo 50 caracteres");
            }
            if (endereco.getCep() != null && endereco.getCep().length() != 8) {
                throw new ValidacaoException("CEP deve conter 8 dígitos");
            }
            if (endereco.getApelido() != null && endereco.getApelido().length() > 50) {
                throw new ValidacaoException("Apelido deve ter no máximo 50 caracteres");
            }
            if (endereco.getObservacoes() != null && endereco.getObservacoes().length() > 255) {
                throw new ValidacaoException("Observações deve ter no máximo 255 caracteres");
            }

            // ===== 4. ASSOCIAR AO CLIENTE E SALVAR =====
            endereco.setCliente(cliente);
            cliente.getEnderecos().add(endereco);  // Mantém a relação bidirecional

            Endereco enderecoSalvo = enderecoRepository.save(endereco);

            // ===== 5. PREPARAR RESPOSTA PARA O FRONT-END =====
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("mensagem", "Endereço adicionado com sucesso!");
            response.put("enderecoId", enderecoSalvo.getId());

            // Monta uma descrição amigável para exibir no select do front-end
            // Ex: "Rua Augusta, 123 - São Paulo/SP"
            String descricao = String.format("%s %s, %s - %s/%s",
                    enderecoSalvo.getTipoLogradouro().getDescricao(),
                    enderecoSalvo.getRua(),
                    enderecoSalvo.getNumero(),
                    enderecoSalvo.getCidade(),
                    enderecoSalvo.getUf().getSigla()
            );
            response.put("enderecoDescricao", descricao);

            return ResponseEntity.ok(response);

        } catch (ClienteNaoEncontradoException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Valor inválido para tipo de logradouro ou UF: " + e.getMessage()));
        } catch (ValidacaoException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();  // Em produção, usar logger
            return ResponseEntity.internalServerError().body(Map.of("erro", "Erro ao salvar endereço: " + e.getMessage()));
        }
    }
}