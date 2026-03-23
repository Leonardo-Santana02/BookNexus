package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.venda.Pagamento;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.enums.FormaPagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPagamento;
import br.com.java.e_commerce.nexus.service.venda.PagamentoService;
import br.com.java.e_commerce.nexus.service.venda.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;
    private final PedidoService pedidoService;

    public PagamentoController(PagamentoService pagamentoService, PedidoService pedidoService) {
        this.pagamentoService = pagamentoService;
        this.pedidoService = pedidoService;
    }

    // ===== PÁGINAS =====

    /**
     * Lista todos os pagamentos (área administrativa)
     */
    @GetMapping
    public String listarPagamentos(Model model) {
        model.addAttribute("pagamentos", pagamentoService.listarTodos());
        return "admin/pagamentos/lista";
    }

    /**
     * Página de detalhes do pagamento
     */
    @GetMapping("/{id}")
    public String detalhesPagamento(@PathVariable Long id, Model model) {
        Pagamento pagamento = pagamentoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + id));

        model.addAttribute("pagamento", pagamento);
        return "admin/pagamentos/detalhes";
    }

    /**
     * Página de pagamento para um pedido específico
     */
    @GetMapping("/pedido/{pedidoId}")
    public String pagamentoPedido(@PathVariable Long pedidoId, Model model) {
        Pedido pedido = pedidoService.buscarPorId(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        // Verificar se já existe pagamento para este pedido
        Pagamento pagamento = pagamentoService.buscarPorPedidoId(pedidoId)
                .orElseGet(() -> {
                    Pagamento novo = new Pagamento();
                    novo.setPedido(pedido);
                    novo.setValor(pedido.getValorTotal());
                    novo.setStatus(StatusPagamento.PENDENTE);
                    return novo;
                });

        model.addAttribute("pagamento", pagamento);
        model.addAttribute("pedido", pedido);
        model.addAttribute("formasPagamento", FormaPagamento.values());
        model.addAttribute("cartoes", pedido.getCliente().getCartoesCredito());

        return "cliente/pagamentos/form";
    }

    // ===== AÇÕES COM RETORNO DE PÁGINA =====

    /**
     * Salvar pagamento (método tradicional)
     */
    @PostMapping("/salvar")
    public String salvarPagamento(@ModelAttribute Pagamento pagamento,
                                  @RequestParam(required = false) List<Long> cuponsIds,
                                  RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pagamento.getPedido();
            // Converter para o formato esperado pelo service
            Map<Long, BigDecimal> cartoesValores = new HashMap<>();
            Map<Long, Integer> cartoesParcelas = new HashMap<>();

            // Aqui você precisaria processar os dados do formulário
            // Este é um exemplo simplificado

            Pagamento pagamentoProcessado = pagamentoService.processarPagamento(
                    pedido.getId(), cuponsIds, cartoesValores, cartoesParcelas);

            redirectAttributes.addFlashAttribute("sucesso",
                    "Pagamento registrado com sucesso! ID: " + pagamentoProcessado.getId());

            return "redirect:/pedidos/" + pedido.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao processar pagamento: " + e.getMessage());
            return "redirect:/pagamentos/pedido/" + pagamento.getPedido().getId();
        }
    }

    /**
     * Confirmar pagamento (aprovar)
     */
    @PostMapping("/{id}/confirmar")
    public String confirmarPagamento(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Pagamento pagamento = pagamentoService.confirmarPagamento(id);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Pagamento confirmado com sucesso! Pedido #" + pagamento.getPedido().getId() + " liberado para envio.");
            return "redirect:/pedidos/" + pagamento.getPedido().getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao confirmar pagamento: " + e.getMessage());
            return "redirect:/pagamentos/" + id;
        }
    }

    /**
     * Rejeitar pagamento
     */
    @PostMapping("/{id}/rejeitar")
    public String rejeitarPagamento(@PathVariable Long id,
                                    @RequestParam String motivo,
                                    RedirectAttributes redirectAttributes) {
        try {
            Pagamento pagamento = pagamentoService.rejeitarPagamento(id, motivo);
            redirectAttributes.addFlashAttribute("sucesso", "Pagamento rejeitado.");
            return "redirect:/pedidos/" + pagamento.getPedido().getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao rejeitar pagamento: " + e.getMessage());
            return "redirect:/pagamentos/" + id;
        }
    }

    // ===== API REST (AJAX) =====

    /**
     * Validar pagamento antes de processar (para um pedido existente)
     */
    @PostMapping("/api/validar-pedido")
    @ResponseBody
    public ResponseEntity<?> validarPagamentoPedido(@RequestBody Map<String, Object> dados) {
        try {
            Long pedidoId = Long.valueOf(dados.get("pedidoId").toString());

            // Processar cupons
            List<Long> cuponsIds = extrairCupons(dados.get("cupons"));

            // Processar cartões
            Map<Long, BigDecimal> cartoesValores = extrairCartoesValores(dados.get("cartoes"));

            PagamentoService.ValidacaoPedido validacao =
                    pagamentoService.validarPagamentoPedido(pedidoId, cuponsIds, cartoesValores);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("valido", true);
            resposta.put("totalPedido", validacao.totalPedido);
            resposta.put("totalDescontoCupons", validacao.totalDescontoCupons);
            resposta.put("totalAPagar", validacao.totalAPagar);
            resposta.put("totalCartoes", validacao.totalCartoes);

            return ResponseEntity.ok(resposta);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valido", false,
                    "erro", e.getMessage()
            ));
        }
    }

    /**
     * Processar pagamento via API
     */
    @PostMapping("/api/processar")
    @ResponseBody
    public ResponseEntity<?> processarPagamentoApi(@RequestBody Map<String, Object> dados) {
        try {
            Long pedidoId = Long.valueOf(dados.get("pedidoId").toString());

            // Processar cupons
            List<Long> cuponsIds = extrairCupons(dados.get("cupons"));

            // Processar cartões (valores e parcelas)
            Map<Long, BigDecimal> cartoesValores = new HashMap<>();
            Map<Long, Integer> cartoesParcelas = new HashMap<>();

            Object cartoesObj = dados.get("cartoes");
            if (cartoesObj instanceof List<?>) {
                List<?> cartoesList = (List<?>) cartoesObj;
                for (Object item : cartoesList) {
                    if (item instanceof Map<?, ?>) {
                        Map<?, ?> cartaoMap = (Map<?, ?>) item;
                        Long cartaoId = Long.valueOf(cartaoMap.get("id").toString());
                        BigDecimal valor = new BigDecimal(cartaoMap.get("valor").toString());
                        Integer parcelas = cartaoMap.get("parcelas") != null ?
                                Integer.valueOf(cartaoMap.get("parcelas").toString()) : 1;

                        cartoesValores.put(cartaoId, valor);
                        cartoesParcelas.put(cartaoId, parcelas);
                    }
                }
            }

            Pagamento pagamento = pagamentoService.processarPagamento(
                    pedidoId, cuponsIds, cartoesValores, cartoesParcelas);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("sucesso", true);
            resposta.put("pagamentoId", pagamento.getId());
            resposta.put("status", pagamento.getStatus().toString());
            resposta.put("mensagem", "Pagamento registrado. Aguardando confirmação.");

            return ResponseEntity.ok(resposta);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "erro", e.getMessage()
            ));
        }
    }

    /**
     * Confirmar pagamento via API
     */
    @PostMapping("/api/{id}/confirmar")
    @ResponseBody
    public ResponseEntity<?> confirmarPagamentoApi(@PathVariable Long id) {
        try {
            Pagamento pagamento = pagamentoService.confirmarPagamento(id);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("sucesso", true);
            resposta.put("pagamentoId", pagamento.getId());
            resposta.put("status", pagamento.getStatus().toString());
            resposta.put("pedidoId", pagamento.getPedido().getId());

            return ResponseEntity.ok(resposta);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "erro", e.getMessage()
            ));
        }
    }

    /**
     * Buscar dados do pagamento via API
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> buscarPagamentoApi(@PathVariable Long id) {
        return pagamentoService.buscarPorId(id)
                .map(pagamento -> {
                    Map<String, Object> dados = new HashMap<>();
                    dados.put("id", pagamento.getId());
                    dados.put("pedidoId", pagamento.getPedido().getId());
                    dados.put("valor", pagamento.getValor());
                    dados.put("desconto", pagamento.getDesconto());
                    dados.put("status", pagamento.getStatus().toString());
                    dados.put("formaPagamento", pagamento.getFormaPagamento().toString());
                    dados.put("dataPagamento", pagamento.getDataPagamento());
                    dados.put("resumoCupons", pagamento.getResumoCupons());
                    dados.put("resumoCartoes", pagamento.getResumoCartoes());
                    return ResponseEntity.ok(dados);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Buscar pagamentos por pedido via API
     */
    @GetMapping("/api/pedido/{pedidoId}")
    @ResponseBody
    public ResponseEntity<?> buscarPorPedidoApi(@PathVariable Long pedidoId) {
        return pagamentoService.buscarPorPedidoId(pedidoId)
                .map(pagamento -> {
                    Map<String, Object> dados = new HashMap<>();
                    dados.put("id", pagamento.getId());
                    dados.put("status", pagamento.getStatus().toString());
                    dados.put("valor", pagamento.getValor());
                    return ResponseEntity.ok(dados);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== MÉTODOS AUXILIARES =====

    private List<Long> extrairCupons(Object cuponsObj) {
        List<Long> cuponsIds = new ArrayList<>();
        if (cuponsObj instanceof List<?>) {
            List<?> tmp = (List<?>) cuponsObj;
            for (Object o : tmp) {
                if (o == null) continue;
                if (o instanceof Number) {
                    cuponsIds.add(((Number) o).longValue());
                } else {
                    cuponsIds.add(Long.valueOf(o.toString()));
                }
            }
        }
        return cuponsIds.isEmpty() ? null : cuponsIds;
    }

    private Map<Long, BigDecimal> extrairCartoesValores(Object cartoesObj) {
        Map<Long, BigDecimal> cartoesValores = new HashMap<>();
        if (cartoesObj instanceof Map<?, ?>) {
            Map<?, ?> cartoesData = (Map<?, ?>) cartoesObj;
            cartoesData.forEach((k, v) -> {
                Long cartaoId = Long.valueOf(k.toString());
                BigDecimal valor = (v == null) ? BigDecimal.ZERO : new BigDecimal(v.toString());
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    cartoesValores.put(cartaoId, valor);
                }
            });
        }
        return cartoesValores;
    }

    private Map<Long, Integer> extrairCartoesParcelas(Object parcelasObj) {
        Map<Long, Integer> cartoesParcelas = new HashMap<>();
        if (parcelasObj instanceof Map<?, ?>) {
            Map<?, ?> parcelasData = (Map<?, ?>) parcelasObj;
            parcelasData.forEach((k, v) -> {
                Long cartaoId = Long.valueOf(k.toString());
                Integer parcelas = (v == null) ? 1 : Integer.valueOf(v.toString());
                cartoesParcelas.put(cartaoId, parcelas);
            });
        }
        return cartoesParcelas;
    }
}