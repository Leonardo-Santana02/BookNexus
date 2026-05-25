package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.enums.MotivoDevolucao;
import br.com.java.e_commerce.nexus.model.enums.StatusSolicitacao;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.venda.SolicitacaoDevolucao;
import br.com.java.e_commerce.nexus.service.venda.DevolucaoService;
import br.com.java.e_commerce.nexus.service.venda.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/devolucoes")
public class DevolucaoController {

    private final DevolucaoService devolucaoService;
    private final PedidoService pedidoService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public DevolucaoController(DevolucaoService devolucaoService, PedidoService pedidoService) {
        this.devolucaoService = devolucaoService;
        this.pedidoService = pedidoService;
    }

    // ============================================================
    // REDIRECIONAMENTOS PARA COMPATIBILIDADE DE URLS
    // ============================================================

    /**
     * Redireciona de /admin/devolucoes para /devolucoes/admin
     * Corrige o erro de acesso à tela de devoluções do admin
     */
    @GetMapping(value = {"/admin/devolucoes", "/admin/devolucoes/"})
    public String redirectAdminDevolucoes() {
        return "redirect:/devolucoes/admin";
    }

    /**
     * Redireciona de /admin/devolucoes/{id} para /devolucoes/admin/{id}
     * Mantém compatibilidade com links que apontam para o padrão antigo
     */
    @GetMapping("/admin/devolucoes/{id}")
    public String redirectAdminDevolucaoDetalhe(@PathVariable Long id) {
        return "redirect:/devolucoes/admin/" + id;
    }

    // ============================================================
    // PÁGINAS DO CLIENTE
    // ============================================================

    /**
     * Exibe o formulário para solicitar devolução de um pedido.
     * SEM exigência de login - acesso livre para qualquer pessoa.
     * Apenas valida se o pedido existe.
     */
    @GetMapping("/solicitar-troca/{pedidoId}")
    public String formSolicitarTroca(@PathVariable Long pedidoId, Model model) {
        // NÃO há verificação de login - acesso livre
        // NÃO há verificação de status do pedido
        // NÃO há verificação se o pedido pertence a algum cliente

        try {
            Pedido pedido = pedidoService.buscarPorId(pedidoId)
                    .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

            model.addAttribute("pedido", pedido);
            model.addAttribute("motivos", MotivoDevolucao.values());

        } catch (Exception e) {
            model.addAttribute("erro", e.getMessage());
            return "redirect:/home";
        }

        return "cliente/solicitar-troca";
    }

    // ============================================================
    // AÇÕES DO CLIENTE
    // ============================================================

    /**
     * Envia uma solicitação de devolução (com itens específicos).
     * SEM exigência de login - qualquer pessoa pode enviar.
     * Obtém o clienteId a partir do pedido.
     */
    @PostMapping("/solicitar")
    public String solicitarDevolucao(@RequestParam Long pedidoId,
                                     @RequestParam(required = false) List<Long> itensIds,
                                     @RequestParam MotivoDevolucao motivo,
                                     @RequestParam(required = false) String justificativa,
                                     RedirectAttributes redirectAttributes) {

        try {
            // Busca o pedido para obter o clienteId
            Pedido pedido = pedidoService.buscarPorId(pedidoId)
                    .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

            Long clienteId = pedido.getCliente().getId();

            SolicitacaoDevolucao solicitacao;

            // Se nenhum item específico foi selecionado, devolve o pedido completo
            if (itensIds == null || itensIds.isEmpty()) {
                solicitacao = devolucaoService.solicitarDevolucaoTotal(
                        pedidoId, clienteId, motivo, justificativa);
            } else {
                solicitacao = devolucaoService.solicitarDevolucao(
                        pedidoId, clienteId, itensIds, motivo, justificativa);
            }

            redirectAttributes.addFlashAttribute("sucesso",
                    "Solicitação #" + solicitacao.getId() + " enviada com sucesso! Aguarde a análise do administrador.");

            // Redireciona para a página inicial ou de sucesso
            return "redirect:/home?sucesso=true";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/devolucoes/solicitar-troca/" + pedidoId;
        }
    }

    /**
     * Envia uma solicitação de devolução (pedido completo) - mantido para compatibilidade
     * SEM exigência de login - qualquer pessoa pode enviar.
     */
    @PostMapping("/solicitar-total")
    public String solicitarDevolucaoTotal(@RequestParam Long pedidoId,
                                          @RequestParam MotivoDevolucao motivo,
                                          @RequestParam(required = false) String justificativa,
                                          RedirectAttributes redirectAttributes) {

        try {
            // Busca o pedido para obter o clienteId
            Pedido pedido = pedidoService.buscarPorId(pedidoId)
                    .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

            Long clienteId = pedido.getCliente().getId();

            SolicitacaoDevolucao solicitacao = devolucaoService.solicitarDevolucaoTotal(
                    pedidoId, clienteId, motivo, justificativa);

            redirectAttributes.addFlashAttribute("sucesso",
                    "Solicitação #" + solicitacao.getId() + " enviada com sucesso! Aguarde a análise do administrador.");

            return "redirect:/home?sucesso=true";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/devolucoes/solicitar-troca/" + pedidoId;
        }
    }

    /**
     * Cancela uma solicitação de devolução (apenas se estiver pendente).
     * Requer login por segurança.
     */
    @PostMapping("/{id}/cancelar")
    public String cancelarSolicitacao(@PathVariable Long id,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {

        Long clienteId = (Long) session.getAttribute("clienteId");
        if (clienteId == null) {
            return "redirect:/login";
        }

        try {
            devolucaoService.cancelarSolicitacao(id, clienteId);
            redirectAttributes.addFlashAttribute("sucesso", "Solicitação cancelada com sucesso!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        // Redireciona para a lista de pedidos do cliente
        return "redirect:/pedidos/cliente/" + clienteId;
    }

    // ============================================================
    // PÁGINAS DO ADMINISTRADOR
    // ============================================================

    @GetMapping("/admin")
    public String listarTodas(Model model,
                              @RequestParam(required = false) StatusSolicitacao status) {

        List<SolicitacaoDevolucao> solicitacoes;
        if (status != null) {
            solicitacoes = devolucaoService.listarPorStatus(status);
            model.addAttribute("statusFiltro", status);
        } else {
            solicitacoes = devolucaoService.listarTodas();
        }

        long pendentes = devolucaoService.listarPorStatus(StatusSolicitacao.PENDENTE).size();
        long aprovadas = devolucaoService.listarPorStatus(StatusSolicitacao.APROVADA).size();
        long recebidas = devolucaoService.listarPorStatus(StatusSolicitacao.RECEBIDA).size();
        long concluidas = devolucaoService.listarPorStatus(StatusSolicitacao.CONCLUIDA).size();

        model.addAttribute("solicitacoes", solicitacoes);
        model.addAttribute("total", solicitacoes.size());
        model.addAttribute("pendentes", pendentes);
        model.addAttribute("aprovadas", aprovadas);
        model.addAttribute("recebidas", recebidas);
        model.addAttribute("concluidas", concluidas);
        model.addAttribute("statusList", StatusSolicitacao.values());

        return "admin/devolucoes/lista";
    }

    @GetMapping("/admin/{id}")
    public String detalhesSolicitacao(@PathVariable Long id, Model model) {
        try {
            SolicitacaoDevolucao solicitacao = devolucaoService.buscarPorId(id);
            model.addAttribute("solicitacao", solicitacao);
            model.addAttribute("motivos", MotivoDevolucao.values());
            model.addAttribute("statusList", StatusSolicitacao.values());

        } catch (Exception e) {
            model.addAttribute("erro", e.getMessage());
            return "redirect:/devolucoes/admin";
        }

        return "admin/devolucoes/detalhes";
    }

    // ============================================================
    // AÇÕES DO ADMINISTRADOR (Form POST)
    // ============================================================

    @PostMapping("/admin/{id}/aprovar")
    public String aprovarSolicitacao(@PathVariable Long id,
                                     @RequestParam(required = false) BigDecimal valorAprovado,
                                     @RequestParam(required = false) String observacao,
                                     RedirectAttributes redirectAttributes) {

        try {
            devolucaoService.aprovarSolicitacao(id, valorAprovado, observacao);
            redirectAttributes.addFlashAttribute("sucesso", "Solicitação aprovada! Aguardando recebimento do produto.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/devolucoes/admin/" + id;
    }

    @PostMapping("/admin/{id}/recusar")
    public String recusarSolicitacao(@PathVariable Long id,
                                     @RequestParam String motivoRecusa,
                                     @RequestParam(required = false) String observacao,
                                     RedirectAttributes redirectAttributes) {

        try {
            devolucaoService.recusarSolicitacao(id, motivoRecusa, observacao);
            redirectAttributes.addFlashAttribute("info", "Solicitação recusada.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/devolucoes/admin/" + id;
    }

    @PostMapping("/admin/{id}/receber")
    public String confirmarRecebimento(@PathVariable Long id,
                                       RedirectAttributes redirectAttributes) {

        try {
            devolucaoService.confirmarRecebimentoProdutos(id);
            redirectAttributes.addFlashAttribute("sucesso", "Recebimento confirmado! Produtos retornaram ao estoque.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/devolucoes/admin/" + id;
    }

    @PostMapping("/admin/{id}/concluir-cupom")
    public String concluirComCupom(@PathVariable Long id,
                                   RedirectAttributes redirectAttributes) {

        try {
            var cupom = devolucaoService.concluirDevolucaoComCupom(id);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Devolução concluída! Cupom de troca gerado: " + cupom.getCodigo());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/devolucoes/admin/" + id;
    }

    // ============================================================
    // API REST (AJAX) - PARA INTEGRAÇÃO COM O PAINEL ADMIN
    // ============================================================

    /**
     * API: Lista todas as solicitações (para AJAX do admin)
     */
    @GetMapping("/api/admin/listar")
    @ResponseBody
    public ResponseEntity<?> apiListarTodas(@RequestParam(required = false) String status) {
        try {
            List<SolicitacaoDevolucao> solicitacoes;
            if (status != null && !status.isEmpty() && !"todos".equals(status)) {
                StatusSolicitacao statusEnum = StatusSolicitacao.valueOf(status.toUpperCase());
                solicitacoes = devolucaoService.listarPorStatus(statusEnum);
            } else {
                solicitacoes = devolucaoService.listarTodas();
            }

            // Mapeia para objetos simples (evita problemas de serialização)
            List<Map<String, Object>> response = solicitacoes.stream().map(s -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", s.getId());
                map.put("pedidoId", s.getPedido().getId());
                map.put("clienteNome", s.getCliente().getNome());
                map.put("clienteEmail", s.getCliente().getEmail());
                map.put("status", s.getStatus().toString());
                map.put("statusDescricao", s.getStatus().getDescricao());
                map.put("motivo", s.getMotivo().getDescricao());
                map.put("valorSolicitado", s.getValorSolicitado());
                // ADICIONADO: valor total do pedido (inclui frete e descontos)
                map.put("valorTotalPedido", s.getPedido().getValorTotal());
                map.put("dataSolicitacao", s.getDataSolicitacao().format(DATE_FORMATTER));
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * API: Buscar detalhes completos de uma solicitação
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> apiBuscarPorId(@PathVariable Long id) {
        try {
            SolicitacaoDevolucao s = devolucaoService.buscarPorId(id);

            Map<String, Object> response = new HashMap<>();
            response.put("id", s.getId());
            response.put("pedidoId", s.getPedido().getId());
            response.put("clienteNome", s.getCliente().getNome());
            response.put("clienteEmail", s.getCliente().getEmail());
            response.put("status", s.getStatus().toString());
            response.put("statusDescricao", s.getStatus().getDescricao());
            response.put("motivo", s.getMotivo().getDescricao());
            response.put("motivoKey", s.getMotivo().toString());
            response.put("valorSolicitado", s.getValorSolicitado());
            // ADICIONADO: valor total do pedido (inclui frete e descontos)
            response.put("valorTotalPedido", s.getPedido().getValorTotal());
            response.put("valorAprovado", s.getValorAprovado());
            response.put("justificativa", s.getJustificativa());
            response.put("observacaoAdmin", s.getObservacaoAdmin());
            response.put("dataSolicitacao", s.getDataSolicitacao().format(DATE_FORMATTER));

            if (s.getDataAprovacao() != null) {
                response.put("dataAprovacao", s.getDataAprovacao().format(DATE_FORMATTER));
            }
            if (s.getDataRecebimento() != null) {
                response.put("dataRecebimento", s.getDataRecebimento().format(DATE_FORMATTER));
            }

            response.put("itens", s.getItensDevolvidos().stream().map(item -> {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("produtoTitulo", item.getItemPedido().getProduto().getTitulo());
                itemMap.put("quantidade", item.getQuantidade());
                itemMap.put("valorUnitario", item.getValorUnitarioDevolvido());
                itemMap.put("valorTotal", item.getValorTotal());
                return itemMap;
            }).collect(Collectors.toList()));

            // Adiciona informações adicionais para o admin
            response.put("acoesDisponiveis", getAcoesDisponiveis(s.getStatus()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * API: Aprovar solicitação
     */
    @PostMapping("/api/admin/{id}/aprovar")
    @ResponseBody
    public ResponseEntity<?> apiAprovarSolicitacao(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> dados) {
        try {
            BigDecimal valorAprovado = null;
            String observacao = null;

            if (dados != null) {
                if (dados.containsKey("valorAprovado") && dados.get("valorAprovado") != null) {
                    valorAprovado = new BigDecimal(dados.get("valorAprovado").toString());
                }
                observacao = (String) dados.get("observacao");
            }

            devolucaoService.aprovarSolicitacao(id, valorAprovado, observacao);
            return ResponseEntity.ok(Map.of("sucesso", true, "status", "APROVADA"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * API: Recusar solicitação
     */
    @PostMapping("/api/admin/{id}/recusar")
    @ResponseBody
    public ResponseEntity<?> apiRecusarSolicitacao(@PathVariable Long id,
                                                   @RequestBody Map<String, String> dados) {
        try {
            String motivoRecusa = dados.get("motivoRecusa");
            String observacao = dados.get("observacao");
            devolucaoService.recusarSolicitacao(id, motivoRecusa, observacao);
            return ResponseEntity.ok(Map.of("sucesso", true, "status", "RECUSADA"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * API: Confirmar recebimento do produto
     */
    @PostMapping("/api/admin/{id}/receber")
    @ResponseBody
    public ResponseEntity<?> apiConfirmarRecebimento(@PathVariable Long id) {
        try {
            devolucaoService.confirmarRecebimentoProdutos(id);
            return ResponseEntity.ok(Map.of("sucesso", true, "status", "RECEBIDA"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * API: Concluir devolução e gerar cupom
     */
    @PostMapping("/api/admin/{id}/concluir")
    @ResponseBody
    public ResponseEntity<?> apiConcluirDevolucao(@PathVariable Long id) {
        try {
            var cupom = devolucaoService.concluirDevolucaoComCupom(id);
            return ResponseEntity.ok(Map.of(
                    "sucesso", true,
                    "status", "CONCLUIDA",
                    "cupomCodigo", cupom.getCodigo(),
                    "cupomValor", cupom.getValor()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * API: Cancelar solicitação (admin)
     */
    @PostMapping("/api/admin/{id}/cancelar")
    @ResponseBody
    public ResponseEntity<?> apiCancelarSolicitacao(@PathVariable Long id,
                                                    @RequestBody(required = false) Map<String, String> dados) {
        try {
            // Para cancelamento pelo admin, precisamos do clienteId
            SolicitacaoDevolucao solicitacao = devolucaoService.buscarPorId(id);
            Long clienteId = solicitacao.getCliente().getId();
            devolucaoService.cancelarSolicitacao(id, clienteId);
            return ResponseEntity.ok(Map.of("sucesso", true, "status", "CANCELADA"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // Métodos mantidos para compatibilidade com versões anteriores da API
    @GetMapping("/api/minhas")
    @ResponseBody
    public ResponseEntity<?> apiMinhasSolicitacoes(HttpSession session) {
        Long clienteId = (Long) session.getAttribute("clienteId");
        if (clienteId == null) {
            return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        }

        try {
            List<SolicitacaoDevolucao> solicitacoes = devolucaoService.listarPorCliente(clienteId);
            return ResponseEntity.ok(solicitacoes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/api/admin")
    @ResponseBody
    public ResponseEntity<?> apiListarTodasOld(@RequestParam(required = false) StatusSolicitacao status) {
        try {
            List<SolicitacaoDevolucao> solicitacoes;
            if (status != null) {
                solicitacoes = devolucaoService.listarPorStatus(status);
            } else {
                solicitacoes = devolucaoService.listarTodas();
            }
            return ResponseEntity.ok(solicitacoes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/api/solicitar")
    @ResponseBody
    public ResponseEntity<?> apiSolicitarDevolucao(@RequestBody Map<String, Object> dados,
                                                   HttpSession session) {
        Long clienteId = (Long) session.getAttribute("clienteId");
        if (clienteId == null) {
            return ResponseEntity.status(401).body(Map.of("erro", "Não autenticado"));
        }

        try {
            Long pedidoId = Long.valueOf(dados.get("pedidoId").toString());
            MotivoDevolucao motivo = MotivoDevolucao.valueOf(dados.get("motivo").toString());
            String justificativa = (String) dados.get("justificativa");

            SolicitacaoDevolucao solicitacao;

            if (dados.containsKey("itensIds") && dados.get("itensIds") != null) {
                List<Long> itensIds = ((List<?>) dados.get("itensIds")).stream()
                        .map(o -> Long.valueOf(o.toString()))
                        .collect(Collectors.toList());
                solicitacao = devolucaoService.solicitarDevolucao(pedidoId, clienteId, itensIds, motivo, justificativa);
            } else {
                solicitacao = devolucaoService.solicitarDevolucaoTotal(pedidoId, clienteId, motivo, justificativa);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("solicitacaoId", solicitacao.getId());
            response.put("status", solicitacao.getStatus().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // ============================================================
    // MÉTODOS AUXILIARES
    // ============================================================

    /**
     * Retorna as ações disponíveis para cada status da solicitação
     */
    private Map<String, Boolean> getAcoesDisponiveis(StatusSolicitacao status) {
        Map<String, Boolean> acoes = new HashMap<>();
        acoes.put("podeAprovar", status == StatusSolicitacao.PENDENTE);
        acoes.put("podeRecusar", status == StatusSolicitacao.PENDENTE);
        acoes.put("podeReceber", status == StatusSolicitacao.APROVADA);
        acoes.put("podeConcluir", status == StatusSolicitacao.RECEBIDA);
        acoes.put("podeCancelar", status == StatusSolicitacao.PENDENTE || status == StatusSolicitacao.APROVADA);
        return acoes;
    }
}