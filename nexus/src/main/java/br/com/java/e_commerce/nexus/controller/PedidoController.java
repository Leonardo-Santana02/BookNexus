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
 */
@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final CupomService cupomService;

    public PedidoController(PedidoService pedidoService,
                            ClienteService clienteService,
                            CupomService cupomService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.cupomService = cupomService;
    }

    // ===== LISTAGENS =====

    @GetMapping
    public String listarTodos(Model model) {
        List<Pedido> pedidos = pedidoService.listarTodos();
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("total", pedidos.stream()
                .map(Pedido::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return "admin/pedidos/lista";
    }

    @GetMapping("/cliente/{clienteId}")
    public String listarPedidosPorCliente(@PathVariable Long clienteId, Model model) {
        Cliente cliente = clienteService.buscarPorId(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        List<Pedido> pedidos = pedidoService.listarPorCliente(clienteId);

        long totalPedidos = pedidos.size();
        long pedidosEmAndamento = pedidos.stream()
                .filter(p -> p.getStatus() == StatusPedido.EM_ABERTO ||
                        p.getStatus() == StatusPedido.PAGO ||
                        p.getStatus() == StatusPedido.ENVIADO)
                .count();

        double valorTotalGasto = pedidos.stream()
                .mapToDouble(p -> p.getValorTotal().doubleValue())
                .sum();

        Set<Integer> anosDisponiveis = new HashSet<>();
        for (Pedido p : pedidos) {
            anosDisponiveis.add(p.getDataCriacao().getYear());
        }

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

    @GetMapping("/{id}")
    public String detalhes(@PathVariable Long id, Model model) {
        Pedido pedido = pedidoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        List<Cupom> cuponsTroca = cupomService.buscarCuponsTrocaAtivosCliente(pedido.getCliente().getId());

        model.addAttribute("pedido", pedido);
        model.addAttribute("cuponsTroca", cuponsTroca);

        return "pedidos/detalhes";
    }

    // ===== CRIAÇÃO =====

    @GetMapping("/criar/{clienteId}")
    public String paginaCriarPedido(@PathVariable Long clienteId, Model model) {
        Cliente cliente = clienteService.buscarPorId(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        model.addAttribute("cliente", cliente);
        model.addAttribute("enderecos", cliente.getEnderecos());

        return "pedidos/criar";
    }

    // ===== PAGAMENTO =====

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

    // ===== RELATÓRIOS (ADMIN) =====

    @GetMapping("/admin/analise")
    public String analiseVendas(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
            @RequestParam(defaultValue = "categoria") String tipo,
            Model model) {

        if (dataInicio == null) dataInicio = LocalDate.now().minusMonths(1);
        if (dataFim == null) dataFim = LocalDate.now();

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.atTime(23, 59, 59);

        Map<String, ?> dados;
        if ("produto".equalsIgnoreCase(tipo)) {
            dados = pedidoService.calcularVendasPorProduto(inicio, fim);
        } else {
            dados = pedidoService.calcularVendasPorCategoria(inicio, fim);
        }

        model.addAttribute("dados", dados);
        model.addAttribute("dataInicio", dataInicio);
        model.addAttribute("dataFim", dataFim);
        model.addAttribute("tipo", tipo);
        model.addAttribute("totalVendas", pedidoService.calcularTotalVendas(inicio, fim));
        model.addAttribute("quantidadeVendas", pedidoService.contarVendas(inicio, fim));

        return "admin/pedidos/analise";
    }

    // ===== API REST =====

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Pedido> buscarPorIdApi(@PathVariable Long id) {
        return pedidoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/cliente/{clienteId}")
    @ResponseBody
    public ResponseEntity<List<Pedido>> listarPorClienteApi(@PathVariable Long clienteId) {
        return ResponseEntity.ok(pedidoService.listarPorCliente(clienteId));
    }

    @GetMapping("/api/analise/vendas")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analiseVendasApi(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataFim) {

        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.atTime(23, 59, 59);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("total", pedidoService.calcularTotalVendas(inicio, fim));
        resultado.put("quantidade", pedidoService.contarVendas(inicio, fim));
        resultado.put("porCategoria", pedidoService.calcularVendasPorCategoria(inicio, fim));
        resultado.put("porProduto", pedidoService.calcularVendasPorProduto(inicio, fim));

        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/api/{id}/detalhes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obterDetalhesPedido(@PathVariable Long id) {
        Pedido pedido = pedidoService.buscarComDetalhes(id);

        Map<String, Object> detalhes = new HashMap<>();
        detalhes.put("id", pedido.getId());
        detalhes.put("dataCriacao", pedido.getDataCriacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        detalhes.put("status", pedido.getStatus().getDescricao());
        detalhes.put("subtotal", pedido.getSubtotal());
        detalhes.put("descontoPromocional", pedido.getDescontoPromocional());
        detalhes.put("valorFrete", pedido.getValorFrete());
        detalhes.put("valorTotal", pedido.getValorTotal());
        detalhes.put("resumoCupons", pedido.getResumoCuponsPromocionais());

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