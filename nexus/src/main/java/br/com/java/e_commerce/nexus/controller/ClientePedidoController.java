package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.service.cliente.ClienteService;
import br.com.java.e_commerce.nexus.service.venda.PedidoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * Controlador responsável por gerenciar a visualização de pedidos do cliente.
 *
 * Este controlador permite que clientes autenticados visualizem seu histórico
 * de pedidos, com filtros por status e ano, além de ver detalhes específicos
 * de cada pedido.
 *
 * @Controller: Marca a classe como um controlador Spring MVC
 * @RequestMapping("/cliente"): Todas as URLs deste controlador começam com "/cliente"
 *
 * URLs disponíveis:
 * - GET /cliente/pedidos              → Listar pedidos do cliente logado
 * - GET /cliente/pedidos/detalhe/{id} → Ver detalhes de um pedido específico
 *
 * IMPORTANTE: Este controlador usa sessão HTTP para identificar o cliente,
 * ao invés de parâmetros de requisição como no CarrinhoController.
 * Em um sistema completo com Spring Security, isso seria feito de forma mais robusta.
 */
@Controller
@RequestMapping("/cliente")
public class ClientePedidoController {

    // Serviços injetados
    private final PedidoService pedidoService;   // Operações com pedidos
    private final ClienteService clienteService; // Operações com clientes

    /**
     * Construtor para injeção de dependências.
     *
     * @param pedidoService Serviço de pedidos
     * @param clienteService Serviço de clientes
     */
    public ClientePedidoController(PedidoService pedidoService, ClienteService clienteService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
    }

    /**
     * Exibe a lista de pedidos do cliente autenticado.
     *
     * Este método é o endpoint principal para o cliente visualizar seu histórico.
     * Ele recupera o ID do cliente da sessão e, se encontrado, exibe seus pedidos.
     *
     * Diferente de outros controladores, este NÃO redireciona para login se o
     * cliente não estiver autenticado. Em vez disso, exibe uma mensagem amigável
     * com um botão para login, melhorando a experiência do usuário.
     *
     * @param session Sessão HTTP (contém "clienteId" se o usuário estiver logado)
     * @param model Model do Spring para passar atributos para a view
     * @return Nome da template Thymeleaf/JSP a ser renderizada
     */
    @GetMapping("/pedidos")
    public String listarMeusPedidos(HttpSession session, Model model) {
        // Recupera o ID do cliente armazenado na sessão durante o login
        Long clienteId = (Long) session.getAttribute("clienteId");

        // ===== CLIENTE NÃO AUTENTICADO =====
        if (clienteId == null) {
            // NÃO redirecionar para login - apenas mostrar mensagem informativa
            // Isso evita loops de redirecionamento e melhora a UX
            model.addAttribute("semPedidos", true);
            model.addAttribute("info", "Faça login para ver seus pedidos");

            // Adiciona flag para exibir botão de login na página
            model.addAttribute("mostrarBotaoLogin", true);

            return "cliente/pedidos";
        }

        // ===== CLIENTE AUTENTICADO =====
        // Chama o método privado que contém a lógica de listagem
        // Passa null para status e ano (sem filtros iniciais)
        return listarPedidosPorCliente(clienteId, null, null, model, session);
    }

    /**
     * Método privado que contém toda a lógica de listagem de pedidos.
     *
     * Este método NÃO é um endpoint público (não tem anotação @GetMapping).
     * Ele é chamado pelo método público listarMeusPedidos().
     *
     * Funcionalidades:
     * 1. Verifica autorização (se o cliente da sessão é o mesmo sendo consultado)
     * 2. Busca todos os pedidos do cliente
     * 3. Aplica filtros por status e ano (se fornecidos)
     * 4. Calcula estatísticas (total, em transporte, valor gasto)
     * 5. Prepara dados para a view
     *
     * @param clienteId ID do cliente cujos pedidos serão listados
     * @param status Filtro por status do pedido (PAGO, ENVIADO, ENTREGUE, etc.)
     * @param ano Filtro por ano de criação do pedido
     * @param model Model do Spring para atributos da view
     * @param session Sessão HTTP para verificar autorização
     * @return Nome da template a ser renderizada
     */
    private String listarPedidosPorCliente(Long clienteId,
                                           String status,
                                           Integer ano,
                                           Model model,
                                           HttpSession session) {
        try {
            // ===== VERIFICAÇÃO DE AUTORIZAÇÃO =====
            // É fundamental garantir que um cliente não veja pedidos de outro cliente
            Long sessaoClienteId = (Long) session.getAttribute("clienteId");

            if (sessaoClienteId == null || !sessaoClienteId.equals(clienteId)) {
                model.addAttribute("erro", "Acesso não autorizado");
                model.addAttribute("semPedidos", true);
                return "cliente/pedidos";
            }

            // ===== BUSCA DADOS DO CLIENTE =====
            Cliente cliente = clienteService.buscarPorId(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

            // ===== BUSCA TODOS OS PEDIDOS DO CLIENTE =====
            List<Pedido> pedidos = pedidoService.listarPorCliente(clienteId);

            // ===== APLICA FILTROS (se fornecidos) =====

            // Filtro por status do pedido
            // Ex: "PAGO", "ENVIADO", "ENTREGUE", "CANCELADO"
            if (status != null && !status.isEmpty() && !"todos".equals(status)) {
                pedidos.removeIf(p -> !p.getStatus().name().equals(status));
            }

            // Filtro por ano de criação
            // Ex: 2024, 2025, etc.
            if (ano != null && ano > 0) {
                pedidos.removeIf(p -> p.getDataCriacao().getYear() != ano);
            }

            // ===== CALCULA ESTATÍSTICAS =====
            long totalPedidos = pedidos.size();

            // Conta quantos pedidos estão em transporte (status ENVIADO)
            long pedidosEmTransporte = pedidos.stream()
                    .filter(p -> p.getStatus().name().equals("ENVIADO"))
                    .count();

            // Calcula o valor total gasto em todos os pedidos
            double valorTotalGasto = pedidos.stream()
                    .mapToDouble(p -> p.getValorTotal().doubleValue())
                    .sum();

            // ===== PREPARA ATRIBUTOS PARA A VIEW =====
            model.addAttribute("cliente", cliente);                    // Dados do cliente
            model.addAttribute("pedidos", pedidos);                    // Lista de pedidos
            model.addAttribute("totalPedidos", totalPedidos);          // Quantidade total
            model.addAttribute("pedidosEmTransporte", pedidosEmTransporte); // Em transporte
            model.addAttribute("valorTotalGasto", valorTotalGasto);    // Total gasto
            model.addAttribute("semPedidos", pedidos.isEmpty());       // Flag para view

            // ===== PREPARA ANOS DISPONÍVEIS PARA FILTRO =====
            // Extrai todos os anos distintos dos pedidos para popular o select de filtro
            // Ex: Se o cliente tem pedidos de 2023 e 2024, o filtro mostrará esses anos
            java.util.Set<Integer> anosDisponiveis = new java.util.HashSet<>();
            for (Pedido p : pedidos) {
                anosDisponiveis.add(p.getDataCriacao().getYear());
            }
            model.addAttribute("anosDisponiveis", anosDisponiveis);

        } catch (Exception e) {
            // Em caso de erro, adiciona mensagem e flag de carrinho vazio
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("semPedidos", true);
        }

        return "cliente/pedidos";
    }

    /**
     * Exibe os detalhes de um pedido específico.
     *
     * Este endpoint permite que o cliente veja informações detalhadas de um pedido,
     * como:
     * - Itens comprados (produtos, quantidades, preços)
     * - Endereço de entrega
     * - Forma de pagamento
     * - Status atual e datas importantes
     * - Código de rastreio (se aplicável)
     *
     * @param id ID do pedido a ser visualizado
     * @param model Model do Spring para atributos da view
     * @param session Sessão HTTP (contém "clienteId")
     * @return Nome da template (redireciona para login se não autenticado)
     * @throws RuntimeException Se pedido não for encontrado
     */
    @GetMapping("/pedidos/detalhe/{id}")
    public String detalhePedido(@PathVariable Long id, Model model, HttpSession session) {
        // Recupera o ID do cliente da sessão
        Long clienteId = (Long) session.getAttribute("clienteId");

        // ===== VERIFICA AUTENTICAÇÃO =====
        // Diferente do método de listagem, este REDIRECIONA para login
        // Isso é apropriado porque a página de detalhe exige autenticação obrigatória
        if (clienteId == null) {
            return "redirect:/login";
        }

        // ===== BUSCA O PEDIDO =====
        Pedido pedido = pedidoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        // ===== VERIFICA AUTORIZAÇÃO =====
        // Garante que o pedido pertence ao cliente logado
        // Se não pertencer, redireciona para a lista de pedidos
        if (!pedido.getCliente().getId().equals(clienteId)) {
            return "redirect:/cliente/pedidos";
        }

        // ===== PREPARA VIEW =====
        model.addAttribute("pedido", pedido);
        return "cliente/pedido-detalhe";
    }
}