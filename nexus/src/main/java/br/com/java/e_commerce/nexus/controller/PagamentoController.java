package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.venda.Pagamento;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.enums.FormaPagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPagamento;
import br.com.java.e_commerce.nexus.model.enums.BandeiraCartao;
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

/**
 * Controlador responsável por gerenciar todo o fluxo de pagamentos do sistema.
 *
 * Este controlador suporta:
 * 1. Páginas administrativas (listagem, detalhes)
 * 2. Página de pagamento para o cliente
 * 3. Ações síncronas (confirmar, rejeitar)
 * 4. API REST para integração com front-end AJAX
 *
 * @Controller: Marca a classe como um controlador Spring MVC
 * @RequestMapping("/pagamentos"): Todas as URLs deste controlador começam com "/pagamentos"
 *
 * URLs disponíveis:
 *
 * ADMINISTRAÇÃO:
 * - GET  /pagamentos                        → Listar todos os pagamentos (admin)
 * - GET  /pagamentos/{id}                   → Detalhes do pagamento (admin)
 *
 * CLIENTE:
 * - GET  /pagamentos/pedido/{pedidoId}      → Página de pagamento do pedido
 *
 * AÇÕES SÍNCRONAS:
 * - POST /pagamentos/salvar                 → Salvar pagamento (tradicional)
 * - POST /pagamentos/{id}/confirmar         → Confirmar pagamento
 * - POST /pagamentos/{id}/rejeitar          → Rejeitar pagamento
 *
 * API REST (AJAX):
 * - POST /pagamentos/api/validar-pedido     → Validar pagamento
 * - POST /pagamentos/api/processar          → Processar pagamento
 * - POST /pagamentos/api/{id}/confirmar     → Confirmar pagamento (REST)
 * - GET  /pagamentos/api/{id}               → Buscar pagamento por ID
 * - GET  /pagamentos/api/pedido/{pedidoId}  → Buscar por pedido
 */
@Controller
@RequestMapping("/pagamentos")
public class PagamentoController {

    // Serviços injetados
    private final PagamentoService pagamentoService;  // Lógica de processamento de pagamentos
    private final PedidoService pedidoService;        // Busca de pedidos

    /**
     * Construtor para injeção de dependências.
     *
     * @param pagamentoService Serviço de pagamentos
     * @param pedidoService Serviço de pedidos
     */
    public PagamentoController(PagamentoService pagamentoService, PedidoService pedidoService) {
        this.pagamentoService = pagamentoService;
        this.pedidoService = pedidoService;
    }

    // ===== PÁGINAS =====

    /**
     * Lista todos os pagamentos do sistema (área administrativa).
     *
     * Exibe uma tabela com todos os pagamentos, seus status, valores,
     * pedidos associados e datas.
     *
     * @param model Model do Spring para passar atributos para a view
     * @return Nome da template Thymeleaf/JSP
     */
    @GetMapping
    public String listarPagamentos(Model model) {
        model.addAttribute("pagamentos", pagamentoService.listarTodos());
        return "admin/pagamentos/lista";
    }

    /**
     * Exibe os detalhes de um pagamento específico (área administrativa).
     *
     * Mostra informações completas do pagamento:
     * - Dados básicos (valor, status, data)
     * - Cupons utilizados
     * - Cartões utilizados com valores e parcelas
     * - Pedido associado
     *
     * @param id ID do pagamento
     * @param model Model do Spring
     * @return Nome da template de detalhes
     * @throws RuntimeException Se pagamento não for encontrado
     */
    @GetMapping("/{id}")
    public String detalhesPagamento(@PathVariable Long id, Model model) {
        Pagamento pagamento = pagamentoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + id));

        model.addAttribute("pagamento", pagamento);
        return "admin/pagamentos/detalhes";
    }

    /**
     * Página de pagamento para um pedido específico (cliente).
     *
     * Esta é a tela principal onde o cliente escolhe as formas de pagamento:
     * - Seleciona cupons de troca disponíveis
     * - Escolhe cartões de crédito e define valores/parcelas
     * - Pode cadastrar um novo cartão
     * - Visualiza o resumo do pedido
     *
     * @param pedidoId ID do pedido a ser pago
     * @param model Model do Spring
     * @return Nome da template "cliente/pagamento"
     * @throws RuntimeException Se pedido não for encontrado
     */
    @GetMapping("/pedido/{pedidoId}")
    public String pagamentoPedido(@PathVariable Long pedidoId, Model model) {
        // ===== 1. BUSCA O PEDIDO =====
        Pedido pedido = pedidoService.buscarPorId(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        // ===== 2. VERIFICA SE JÁ EXISTE PAGAMENTO =====
        // Se já existe, reutiliza; se não, cria um novo pagamento pendente
        Pagamento pagamento = pagamentoService.buscarPorPedidoId(pedidoId)
                .orElseGet(() -> {
                    Pagamento novo = new Pagamento();
                    novo.setPedido(pedido);
                    novo.setValor(pedido.getValorTotal());
                    novo.setStatus(StatusPagamento.PENDENTE);
                    return novo;
                });

        // ===== 3. PREPARA ATRIBUTOS PARA A VIEW =====
        model.addAttribute("pagamento", pagamento);
        model.addAttribute("pedido", pedido);

        // Formas de pagamento disponíveis (ex: CARTAO_CREDITO, BOLETO, PIX)
        model.addAttribute("formasPagamento", FormaPagamento.values());

        // Cartões de crédito salvos do cliente
        model.addAttribute("cartoes", pedido.getCliente().getCartoesCredito());

        // Bandeiras de cartão disponíveis (VISA, MASTERCARD, ELO, AMEX)
        model.addAttribute("bandeiras", BandeiraCartao.values());

        // CORREÇÃO: Template em "cliente/pagamento" (não "cliente/pagamentos")
        return "cliente/pagamento";
    }

    // ===== AÇÕES COM RETORNO DE PÁGINA (TRADICIONAL) =====

    /**
     * Salva/processa um pagamento via formulário tradicional.
     *
     * NOTA: Este método está simplificado. Em produção, seria mais completo,
     * processando os dados do formulário para extrair cartões e cupons.
     *
     * @param pagamento Objeto Pagamento do formulário
     * @param cuponsIds Lista de IDs dos cupons utilizados
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página do pedido ou voltar ao pagamento
     */
    @PostMapping("/salvar")
    public String salvarPagamento(@ModelAttribute Pagamento pagamento,
                                  @RequestParam(required = false) List<Long> cuponsIds,
                                  RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pagamento.getPedido();

            // TODO: Processar os dados do formulário para extrair cartões
            // Este é um exemplo simplificado - em produção, os cartões viriam
            // do formulário com valores e parcelas
            Map<Long, BigDecimal> cartoesValores = new HashMap<>();
            Map<Long, Integer> cartoesParcelas = new HashMap<>();

            // Processa o pagamento através do serviço
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
     * Confirma/aprova um pagamento (área administrativa).
     *
     * Esta ação é executada por um administrador quando o pagamento é aprovado
     * pela operadora de cartão ou quando o boleto é compensado.
     *
     * @param id ID do pagamento a ser confirmado
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página do pedido
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
     * Rejeita um pagamento (área administrativa).
     *
     * Usado quando o pagamento é negado pela operadora, cartão com saldo
     * insuficiente, ou suspeita de fraude.
     *
     * @param id ID do pagamento a ser rejeitado
     * @param motivo Motivo da rejeição (ex: "Saldo insuficiente")
     * @param redirectAttributes Para mensagens flash
     * @return Redirecionamento para página do pedido
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

    // ===== API REST (AJAX) - INTEGRAÇÃO COM FRONT-END MODERNO =====

    /**
     * Valida um pagamento antes de processar (via AJAX).
     *
     * Este endpoint é chamado pelo front-end quando o cliente preenche os dados
     * de pagamento, antes de confirmar a compra. Ele verifica:
     * - Se os cupons são válidos
     * - Se os valores dos cartões cobrem o total
     * - Se há erros nas regras de negócio
     *
     * @param dados Mapa com os dados do pagamento (pedidoId, cupons, cartões)
     * @return ResponseEntity com resultado da validação
     *
     * @example Body da requisição:
     * {
     *   "pedidoId": 123,
     *   "cupons": [1, 2],
     *   "cartoes": {"1": 50.00, "2": 30.00}
     * }
     */
    @PostMapping("/api/validar-pedido")
    @ResponseBody
    public ResponseEntity<?> validarPagamentoPedido(@RequestBody Map<String, Object> dados) {
        try {
            // Extrai o ID do pedido do corpo da requisição
            Long pedidoId = Long.valueOf(dados.get("pedidoId").toString());

            // Processa a lista de cupons (pode ser null ou vazio)
            List<Long> cuponsIds = extrairCupons(dados.get("cupons"));

            // Processa os cartões e seus respectivos valores
            Map<Long, BigDecimal> cartoesValores = extrairCartoesValores(dados.get("cartoes"));

            // Chama o serviço para validar o pagamento
            PagamentoService.ValidacaoPedido validacao =
                    pagamentoService.validarPagamentoPedido(pedidoId, cuponsIds, cartoesValores);

            // Prepara resposta de sucesso
            Map<String, Object> resposta = new HashMap<>();
            resposta.put("valido", true);
            resposta.put("totalPedido", validacao.totalPedido);
            resposta.put("totalDescontoCupons", validacao.totalDescontoCupons);
            resposta.put("totalAPagar", validacao.totalAPagar);
            resposta.put("totalCartoes", validacao.totalCartoes);

            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            // Retorna erro com a mensagem para exibir ao cliente
            return ResponseEntity.badRequest().body(Map.of(
                    "valido", false,
                    "erro", e.getMessage()
            ));
        }
    }

    /**
     * Processa um pagamento via API REST (AJAX).
     *
     * Este é o endpoint principal para checkout moderno. Ele recebe todos os
     * dados do pagamento, processa e retorna o resultado.
     *
     * @param dados Mapa completo com os dados do pagamento
     * @return ResponseEntity com resultado do processamento
     *
     * @example Body da requisição:
     * {
     *   "pedidoId": 123,
     *   "cupons": [1, 2],
     *   "cartoes": [
     *     {"id": 1, "valor": 50.00, "parcelas": 3},
     *     {"id": 2, "valor": 30.00, "parcelas": 1}
     *   ]
     * }
     */
    @PostMapping("/api/processar")
    @ResponseBody
    public ResponseEntity<?> processarPagamentoApi(@RequestBody Map<String, Object> dados) {
        try {
            // ===== 1. EXTRAI O ID DO PEDIDO =====
            Long pedidoId = Long.valueOf(dados.get("pedidoId").toString());

            // ===== 2. EXTRAI OS CUPONS =====
            List<Long> cuponsIds = extrairCupons(dados.get("cupons"));

            // ===== 3. EXTRAI OS CARTÕES COM VALORES E PARCELAS =====
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

            // ===== 4. PROCESSA O PAGAMENTO =====
            Pagamento pagamento = pagamentoService.processarPagamento(
                    pedidoId, cuponsIds, cartoesValores, cartoesParcelas);

            // ===== 5. PREPARA RESPOSTA =====
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
     * Confirma um pagamento via API REST.
     *
     * @param id ID do pagamento a ser confirmado
     * @return ResponseEntity com resultado da confirmação
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
     * Busca os dados de um pagamento via API REST.
     *
     * @param id ID do pagamento
     * @return ResponseEntity com os dados do pagamento ou 404
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
     * Busca o pagamento associado a um pedido via API REST.
     *
     * @param pedidoId ID do pedido
     * @return ResponseEntity com os dados do pagamento ou 404
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

    /**
     * Extrai uma lista de IDs de cupons de um objeto recebido na requisição.
     *
     * Suporta diferentes formatos:
     * - Lista de números: [1, 2, 3]
     * - Lista de strings: ["1", "2", "3"]
     * - null ou vazio → retorna null
     *
     * @param cuponsObj Objeto que pode conter a lista de cupons
     * @return Lista de Long com IDs, ou null se vazia
     */
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

    /**
     * Extrai um mapa de cartões com seus respectivos valores.
     *
     * Suporta formato: {"1": 50.00, "2": 30.00}
     *
     * @param cartoesObj Objeto contendo o mapa de cartões
     * @return Mapa com ID do cartão e valor
     */
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

    /**
     * Extrai um mapa de cartões com suas respectivas quantidades de parcelas.
     *
     * @param parcelasObj Objeto contendo o mapa de parcelas
     * @return Mapa com ID do cartão e número de parcelas
     */
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