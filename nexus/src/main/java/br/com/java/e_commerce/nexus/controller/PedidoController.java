package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public String listarPorCliente(@PathVariable Long clienteId, Model model) {
        Cliente cliente = clienteService.buscarPorId(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        List<Pedido> pedidos = pedidoService.listarPorCliente(clienteId);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("cliente", cliente);

        // CORRIGIDO: Alterado de "cliente/pedidos/lista" para "cliente/pedidos"
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

    // ===== DEVOLUÇÃO =====

    @PostMapping("/{id}/solicitar-devolucao")
    public String solicitarDevolucao(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.buscarPorId(id)
                    .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

            if (pedido.getStatus() != StatusPedido.ENTREGUE) {
                throw new RuntimeException("Somente pedidos entregues podem ser devolvidos");
            }

            pedidoService.atualizarStatus(id, StatusPedido.AGUARDANDO_DEVOLUCAO);
            redirectAttributes.addFlashAttribute("sucesso", "Solicitação de devolução enviada!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

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

    @PostMapping("/{id}/negar-devolucao")
    public String negarDevolucao(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.atualizarStatus(id, StatusPedido.ENTREGUE);
            redirectAttributes.addFlashAttribute("info", "Solicitação de devolução negada.");
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

    // ===== API =====

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
}