package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.venda.ItemPedido;
import br.com.java.e_commerce.nexus.model.venda.Pagamento;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import br.com.java.e_commerce.nexus.service.cliente.ClienteService;
import br.com.java.e_commerce.nexus.service.venda.CupomService;
import br.com.java.e_commerce.nexus.service.venda.PedidoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controlador responsável por gerenciar todas as operações relacionadas a pedidos.
<<<<<<< HEAD
=======
 *
 * Este controlador suporta:
 * 1. Páginas administrativas (listagem, análise de vendas)
 * 2. Páginas do cliente (listagem de pedidos, detalhes)
 * 3. Ações de gerenciamento (atualizar status, cancelar, devolução)
 * 4. API REST para integração com front-end
 *
 * @Controller: Marca a classe como um controlador Spring MVC
 * @RequestMapping("/pedidos"): Todas as URLs deste controlador começam com "/pedidos"
 *
 * URLs disponíveis:
 *
 * ADMINISTRAÇÃO:
 * - GET  /pedidos                              → Listar todos os pedidos (admin)
 * - GET  /pedidos/admin/analise                → Análise de vendas (admin)
 *
 * CLIENTE:
 * - GET  /pedidos/cliente/{clienteId}          → Listar pedidos do cliente
 * - GET  /pedidos/criar/{clienteId}            → Página de criação de pedido
 *
 * DETALHES E AÇÕES:
 * - GET  /pedidos/{id}                         → Detalhes do pedido
 * - POST /pedidos/{id}/pagamento               → Processar pagamento
 * - POST /pedidos/{id}/status                  → Atualizar status
 * - POST /pedidos/{id}/cancelar                → Cancelar pedido
 * - POST /pedidos/{id}/solicitar-devolucao     → Solicitar devolução
 * - POST /pedidos/{id}/confirmar-devolucao     → Confirmar devolução (admin)
 * - POST /pedidos/{id}/negar-devolucao         → Negar devolução (admin)
 *
 * API REST:
 * - GET  /pedidos/api/{id}                     → Buscar pedido por ID
 * - GET  /pedidos/api/cliente/{clienteId}      → Listar pedidos do cliente (REST)
 * - GET  /pedidos/api/analise/vendas           → Análise de vendas (REST)
 * - GET  /pedidos/api/{id}/detalhes            → Detalhes completos (pop-up)
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
 */
@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    // Serviços injetados
    private final PedidoService pedidoService;   // Operações com pedidos
    private final ClienteService clienteService; // Busca de clientes
    private final CupomService cupomService;     // Busca de cupons de troca

    /**
     * Construtor para injeção de dependências.
     *
     * @param pedidoService Serviço de pedidos
     * @param clienteService Serviço de clientes
     * @param cupomService Serviço de cupons
     */
    public PedidoController(PedidoService pedidoService,
                            ClienteService clienteService,
                            CupomService cupomService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.cupomService = cupomService;
    }

    // ===== LISTAGENS =====

    /**
     * Lista todos os pedidos do sistema (área administrativa).
     *
     * Exibe uma tabela com todos os pedidos e calcula o valor total
     * de todos os pedidos para o dashboard.
     *
     * @param model Model do Spring
     * @return Nome da template "admin/pedidos/lista"
     */
    @GetMapping
    public String listarTodos(Model model) {
        // Busca todos os pedidos do sistema
        List<Pedido> pedidos = pedidoService.listarTodos();

        model.addAttribute("pedidos", pedidos);

        // Calcula o valor total de todos os pedidos (útil para dashboard)
        model.addAttribute("total", pedidos.stream()
                .map(Pedido::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return "admin/pedidos/lista";
    }

    /**
     * Lista todos os pedidos de um cliente específico.
     *
     * Exibe o histórico de compras do cliente com estatísticas:
     * - Total de pedidos
     * - Pedidos em andamento (abertos, pagos ou enviados)
     * - Valor total gasto
     * - Anos disponíveis para filtro
     *
     * @param clienteId ID do cliente
     * @param model Model do Spring
     * @return Nome da template "cliente/pedidos"
     * @throws RuntimeException Se cliente não for encontrado
     */
    @GetMapping("/cliente/{clienteId}")
    public String listarPedidosPorCliente(@PathVariable Long clienteId, Model model) {
<<<<<<< HEAD
=======
        // ===== 1. BUSCA O CLIENTE =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        Cliente cliente = clienteService.buscarPorId(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        // ===== 2. BUSCA OS PEDIDOS DO CLIENTE =====
        List<Pedido> pedidos = pedidoService.listarPorCliente(clienteId);

<<<<<<< HEAD
        long totalPedidos = pedidos.size();
=======
        // ===== 3. CALCULA ESTATÍSTICAS =====
        // Total de pedidos
        long totalPedidos = pedidos.size();

        // Pedidos em andamento (não finalizados)
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        long pedidosEmAndamento = pedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.EM_ABERTO ||
                        p.getStatus() == StatusPedido.PAGO ||
                        p.getStatus() == StatusPedido.ENVIADO)
                .count();

<<<<<<< HEAD
=======
        // Valor total gasto (soma de todos os pedidos)
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        double valorTotalGasto = pedidos.stream()
                .mapToDouble(p -> p.getValorTotal().doubleValue())
                .sum();

<<<<<<< HEAD
=======
        // ===== 4. EXTRAI ANOS DISPONÍVEIS PARA FILTRO =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        Set<Integer> anosDisponiveis = new HashSet<>();
        for (Pedido p : pedidos) {
            anosDisponiveis.add(p.getDataCriacao().getYear());
        }

<<<<<<< HEAD
=======
        // ===== 5. PREPARA ATRIBUTOS PARA A VIEW =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        model.addAttribute("cliente", cliente);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("totalPedidos", totalPedidos);
        model.addAttribute("pedidosEmAndamento", pedidosEmAndamento);
        model.addAttribute("valorTotalGasto", valorTotalGasto);
        model.addAttribute("anosDisponiveis", anosDisponiveis);
        model.addAttribute("semPedidos", pedidos.isEmpty());

        return "cliente/pedidos";
    }

    // ===== DETALHES =====

    /**
     * Exibe os detalhes de um pedido específico.
     *
     * Mostra informações completas do pedido:
     * - Itens comprados
     * - Endereço de entrega
     * - Pagamento
     * - Cupons de troca disponíveis (para solicitar devolução)
     *
     * @param id ID do pedido
     * @param model Model do Spring
     * @return Nome da template "pedidos/detalhes"
     * @throws RuntimeException Se pedido não for encontrado
     */
    @GetMapping("/{id}")
    public String detalhes(@PathVariable Long id, Model model) {
        // Busca o pedido pelo ID
        Pedido pedido = pedidoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        // Busca cupons de troca disponíveis para o cliente
        // (útil para sugerir devolução ou troca)
        List<Cupom> cuponsTroca = cupomService.buscarCuponsTrocaAtivosCliente(pedido.getCliente().getId());

        model.addAttribute("pedido", pedido);
        model.addAttribute("cuponsTroca", cuponsTroca);

        return "pedidos/detalhes";
    }

    // ===== CRIAÇÃO =====

    /**
     * Exibe a página de criação de pedido para um cliente.
     *
     * NOTA: Este método parece ser para um fluxo administrativo onde
     * um operador cria um pedido manualmente para o cliente.
     *
     * @param clienteId ID do cliente
     * @param model Model do Spring
     * @return Nome da template "pedidos/criar"
     * @throws RuntimeException Se cliente não for encontrado
     */
    @GetMapping("/criar/{clienteId}")
    public String paginaCriarPedido(@PathVariable Long clienteId, Model model) {
        Cliente cliente = clienteService.buscarPorId(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        model.addAttribute("cliente", cliente);
        model.addAttribute("enderecos", cliente.getEnderecos());

        return "pedidos/criar";
    }

    // ===== PAGAMENTO =====

    /**
     * Processa o pagamento de um pedido (versão simplificada).
     *
     * NOTA: Este método é uma versão simplificada. O fluxo completo
     * de pagamento deve ser feito pelo PagamentoService.
     *
     * @param id ID do pedido
     * @param pagamento Objeto Pagamento do formulário
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página de detalhes do pedido
     */
    @PostMapping("/{id}/pagamento")
    public String processarPagamento(@PathVariable Long id,
                                     @ModelAttribute Pagamento pagamento,
                                     RedirectAttributes redirectAttributes) {
        try {
            pedidoService.processarPagamento(id, pagamento);
            redirectAttributes.addFlashAttribute("sucesso", "Pagamento processado com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    // ===== ATUALIZAÇÃO DE STATUS =====

    /**
     * Atualiza o status de um pedido (área administrativa).
     *
     * Status possíveis:
     * - EM_ABERTO → PAGO → ENVIADO → ENTREGUE
     * - CANCELADO (a qualquer momento antes do envio)
     * - AGUARDANDO_DEVOLUCAO → DEVOLUCAO_CONFIRMADA
     *
     * @param id ID do pedido
     * @param status Novo status a ser aplicado
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página de detalhes
     */
    @PostMapping("/{id}/status")
    public String atualizarStatus(@PathVariable Long id,
                                  @RequestParam StatusPedido status,
                                  RedirectAttributes redirectAttributes) {
        try {
            pedidoService.atualizarStatus(id, status);
            redirectAttributes.addFlashAttribute("sucesso", "Status atualizado com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    /**
     * Cancela um pedido.
     *
     * @param id ID do pedido
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página de detalhes
     */
    @PostMapping("/{id}/cancelar")
    public String cancelarPedido(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.cancelarPedido(id);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

<<<<<<< HEAD
=======
    // ===== DEVOLUÇÃO =====

    /**
     * Solicita a devolução de um pedido entregue.
     *
     * @param id ID do pedido
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página de detalhes
     */
    @PostMapping("/{id}/solicitar-devolucao")
    public String solicitarDevolucao(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.buscarPorId(id)
                    .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

            // Regra de negócio: apenas pedidos entregues podem ser devolvidos
            if (pedido.getStatus() != StatusPedido.ENTREGUE) {
                throw new RuntimeException("Somente pedidos entregues podem ser devolvidos");
            }

            // Muda o status para "Aguardando devolução"
            pedidoService.atualizarStatus(id, StatusPedido.AGUARDANDO_DEVOLUCAO);
            redirectAttributes.addFlashAttribute("sucesso", "Solicitação de devolução enviada!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    /**
     * Confirma a devolução e gera cupom de troca (área administrativa).
     *
     * @param id ID do pedido
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página de detalhes
     */
    @PostMapping("/{id}/confirmar-devolucao")
    public String confirmarDevolucao(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Cupom cupom = pedidoService.confirmarDevolucaoEGerarCupom(id);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Devolução confirmada! Cupom de troca gerado: " + cupom.getCodigo());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    /**
     * Nega a solicitação de devolução (área administrativa).
     *
     * @param id ID do pedido
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página de detalhes
     */
    @PostMapping("/{id}/negar-devolucao")
    public String negarDevolucao(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // Retorna o pedido ao status ENTREGUE
            pedidoService.atualizarStatus(id, StatusPedido.ENTREGUE);
            redirectAttributes.addFlashAttribute("info", "Solicitação de devolução negada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
    // ===== RELATÓRIOS (ADMIN) =====

    /**
     * Exibe análise de vendas para administradores.
     *
     * Permite:
     * - Filtrar por período (data de início e fim)
     * - Agrupar por categoria ou por produto
     * - Visualizar total de vendas e quantidade de pedidos
     *
     * @param dataInicio Data inicial do período (padrão: 1 mês atrás)
     * @param dataFim Data final do período (padrão: hoje)
     * @param tipo Tipo de agrupamento: "categoria" ou "produto"
     * @param model Model do Spring
     * @return Nome da template "admin/pedidos/analise"
     */
    @GetMapping("/admin/analise")
    public String analiseVendas(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
            @RequestParam(defaultValue = "categoria") String tipo,
            Model model) {

        // ===== 1. DEFINE PERÍODO PADRÃO =====
        // Se não informado, busca os últimos 30 dias
        if (dataInicio == null) dataInicio = LocalDate.now().minusMonths(1);
        if (dataFim == null) dataFim = LocalDate.now();

        // Converte LocalDate para LocalDateTime (início e fim do dia)
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.atTime(23, 59, 59);

        // ===== 2. BUSCA DADOS CONFORME TIPO =====
        Map<String, ?> dados;
        if ("produto".equalsIgnoreCase(tipo)) {
            // Agrupa vendas por produto
            dados = pedidoService.calcularVendasPorProduto(inicio, fim);
        } else {
            // Agrupa vendas por categoria (gênero)
            dados = pedidoService.calcularVendasPorCategoria(inicio, fim);
        }

        // ===== 3. PREPARA ATRIBUTOS =====
        model.addAttribute("dados", dados);
        model.addAttribute("dataInicio", dataInicio);
        model.addAttribute("dataFim", dataFim);
        model.addAttribute("tipo", tipo);
        model.addAttribute("totalVendas", pedidoService.calcularTotalVendas(inicio, fim));
        model.addAttribute("quantidadeVendas", pedidoService.contarVendas(inicio, fim));

        return "admin/pedidos/analise";
    }

    // ===== API REST =====

    /**
     * Busca um pedido pelo ID via API REST.
     *
     * @param id ID do pedido
     * @return ResponseEntity com o pedido ou 404
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Pedido> buscarPorIdApi(@PathVariable Long id) {
        return pedidoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lista todos os pedidos de um cliente via API REST.
     *
     * @param clienteId ID do cliente
     * @return ResponseEntity com a lista de pedidos
     */
    @GetMapping("/api/cliente/{clienteId}")
    @ResponseBody
    public ResponseEntity<List<Pedido>> listarPorClienteApi(@PathVariable Long clienteId) {
        return ResponseEntity.ok(pedidoService.listarPorCliente(clienteId));
    }

    /**
     * Retorna análise de vendas via API REST.
     *
     * @param dataInicio Data inicial do período
     * @param dataFim Data final do período
     * @return ResponseEntity com total, quantidade, vendas por categoria e produto
     */
    @GetMapping("/api/analise/vendas")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analiseVendasApi(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataFim) {

        // Converte para LocalDateTime
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.atTime(23, 59, 59);

        // Prepara resultado com múltiplas métricas
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total", pedidoService.calcularTotalVendas(inicio, fim));
        resultado.put("quantidade", pedidoService.contarVendas(inicio, fim));
        resultado.put("porCategoria", pedidoService.calcularVendasPorCategoria(inicio, fim));
        resultado.put("porProduto", pedidoService.calcularVendasPorProduto(inicio, fim));

        return ResponseEntity.ok(resultado);
    }

<<<<<<< HEAD
    @GetMapping("/api/{id}/detalhes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obterDetalhesPedido(@PathVariable Long id) {
        Pedido pedido = pedidoService.buscarComDetalhes(id);

=======
    /**
     * Retorna detalhes completos de um pedido para exibição em pop-up.
     *
     * Este endpoint é específico para a tela de listagem de pedidos,
     * onde um pop-up exibe informações detalhadas sem sair da página.
     *
     * Informações retornadas:
     * - Dados básicos (ID, data, status)
     * - Valores (subtotal, desconto, frete, total)
     * - Endereço de entrega formatado
     * - Pagamento (forma, status, data, resumo)
     * - Itens comprados (produto, quantidade, preços)
     *
     * @param id ID do pedido
     * @return ResponseEntity com mapa contendo todos os detalhes
     */
    @GetMapping("/api/{id}/detalhes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obterDetalhesPedido(@PathVariable Long id) {
        // Busca o pedido com detalhes (relacionamentos carregados)
        Pedido pedido = pedidoService.buscarComDetalhes(id);

        // ===== 1. DADOS BÁSICOS =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        Map<String, Object> detalhes = new HashMap<>();
        detalhes.put("id", pedido.getId());
        detalhes.put("dataCriacao", pedido.getDataCriacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        detalhes.put("status", pedido.getStatus().getDescricao());
<<<<<<< HEAD
=======

        // ===== 2. VALORES =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        detalhes.put("subtotal", pedido.getSubtotal());
        detalhes.put("descontoPromocional", pedido.getDescontoPromocional());
        detalhes.put("valorFrete", pedido.getValorFrete());
        detalhes.put("valorTotal", pedido.getValorTotal());
        detalhes.put("resumoCupons", pedido.getResumoCuponsPromocionais());

<<<<<<< HEAD
=======
        // ===== 3. ENDEREÇO DE ENTREGA FORMATADO =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        Endereco end = pedido.getEnderecoEntrega();
        if (end != null) {
            String enderecoStr = String.format("%s %s, %s - %s/%s - CEP %s",
                    end.getTipoLogradouro().getDescricao(),
                    end.getRua(), end.getNumero(),
                    end.getCidade(), end.getUf().getSigla(),
                    end.getCep());
            detalhes.put("enderecoEntrega", enderecoStr);
        } else {
            detalhes.put("enderecoEntrega", "Não informado");
        }

<<<<<<< HEAD
=======
        // ===== 4. INFORMAÇÕES DE PAGAMENTO =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        if (pedido.getPagamento() != null) {
            Pagamento pag = pedido.getPagamento();
            Map<String, Object> pagInfo = new HashMap<>();
            pagInfo.put("forma", pag.getFormaPagamento().getDescricao());
            pagInfo.put("status", pag.getStatus().getDescricao());
            pagInfo.put("data", pag.getDataPagamento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            pagInfo.put("resumoCartoes", pag.getResumoCartoes());
            detalhes.put("pagamento", pagInfo);
        } else {
            detalhes.put("pagamento", null);
        }

<<<<<<< HEAD
=======
        // ===== 5. ITENS DO PEDIDO =====
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
        List<Map<String, Object>> itensList = new ArrayList<>();
        for (ItemPedido item : pedido.getItens()) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("produtoTitulo", item.getProduto().getTitulo());
            itemMap.put("produtoAutor", item.getProduto().getAutor());
            itemMap.put("quantidade", item.getQuantidade());
            itemMap.put("precoUnitario", item.getPrecoUnitario());
            itemMap.put("subtotalItem", item.getPrecoTotal());
            itensList.add(itemMap);
        }
        detalhes.put("itens", itensList);

        return ResponseEntity.ok(detalhes);
    }
}