package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import br.com.java.e_commerce.nexus.service.carrinho.CarrinhoService;
import br.com.java.e_commerce.nexus.service.venda.CupomService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/carrinho")
public class CarrinhoController {

    private final CarrinhoService carrinhoService;
    private final CupomService cupomService;

    public CarrinhoController(CarrinhoService carrinhoService,
                              CupomService cupomService) {
        this.carrinhoService = carrinhoService;
        this.cupomService = cupomService;
    }

    // ===== MÉTODO AUXILIAR =====
    private Long getClienteId() {
        // TODO: Obter cliente logado da sessão
        return 1L;
    }

    private Map<String, Object> montarResumo(Carrinho carrinho) {
        Map<String, Object> response = new HashMap<>();
        response.put("subtotal", carrinho.getSubtotal());
        response.put("desconto", carrinho.getDescontoTotal());
        response.put("total", carrinho.getTotal());
        response.put("quantidadeItens", carrinho.getQuantidadeItens());
        return response;
    }

    // ===== PÁGINA DO CARRINHO =====

    @GetMapping
    public String verCarrinho(Model model) {
        Long clienteId = getClienteId();

        try {
            Carrinho carrinho = carrinhoService.buscarComItens(clienteId);
            model.addAttribute("carrinho", carrinho);
            model.addAttribute("cliente", carrinho.getCliente());

            model.addAttribute("cuponsPromocionais",
                    cupomService.buscarCuponsPromocionaisAtivosCliente(clienteId));
            model.addAttribute("cuponsTroca",
                    cupomService.buscarCuponsTrocaAtivosCliente(clienteId));

        } catch (Exception e) {
            model.addAttribute("erro", "Erro ao carregar carrinho: " + e.getMessage());
        }

        // CORRIGIDO: Adicionado o caminho da pasta "cliente/"
        return "cliente/carrinho";
    }

    // ===== AÇÕES COM RETORNO DE PÁGINA =====

    @PostMapping("/adicionar")
    public String adicionarProduto(@RequestParam Long produtoId,
                                   @RequestParam(defaultValue = "1") int quantidade,
                                   RedirectAttributes redirectAttributes) {

        Long clienteId = getClienteId();

        try {
            carrinhoService.adicionarItem(clienteId, produtoId, quantidade);
            redirectAttributes.addFlashAttribute("sucesso", "Produto adicionado ao carrinho!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    @PostMapping("/aplicar-cupom")
    public String aplicarCupom(@RequestParam String codigoCupom,
                               RedirectAttributes redirectAttributes) {

        Long clienteId = getClienteId();

        try {
            carrinhoService.adicionarCupom(clienteId, codigoCupom);
            redirectAttributes.addFlashAttribute("sucesso", "Cupom aplicado com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    @PostMapping("/limpar")
    public String limparCarrinho(RedirectAttributes redirectAttributes) {

        Long clienteId = getClienteId();

        try {
            carrinhoService.limparCarrinho(clienteId);
            redirectAttributes.addFlashAttribute("sucesso", "Carrinho limpo com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    @GetMapping("/remover/{produtoId}")
    public String removerItemView(@PathVariable Long produtoId,
                                  RedirectAttributes redirectAttributes) {

        Long clienteId = getClienteId();

        try {
            carrinhoService.removerItem(clienteId, produtoId);
            redirectAttributes.addFlashAttribute("sucesso", "Item removido do carrinho!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/carrinho";
    }

    // ===== API REST (AJAX) =====

    @PostMapping("/api/alterar-quantidade")
    @ResponseBody
    public ResponseEntity<?> alterarQuantidade(@RequestParam Long produtoId,
                                               @RequestParam int quantidade) {

        Long clienteId = getClienteId();

        try {
            Carrinho carrinho = carrinhoService.alterarQuantidadeItem(clienteId, produtoId, quantidade);
            return ResponseEntity.ok(montarResumo(carrinho));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/api/remover")
    @ResponseBody
    public ResponseEntity<?> removerItem(@RequestParam Long produtoId) {

        Long clienteId = getClienteId();

        try {
            Carrinho carrinho = carrinhoService.removerItem(clienteId, produtoId);
            return ResponseEntity.ok(montarResumo(carrinho));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/api/remover-cupom")
    @ResponseBody
    public ResponseEntity<?> removerCupom(@RequestParam Long cupomId) {

        Long clienteId = getClienteId();

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

    @GetMapping("/api/resumo")
    @ResponseBody
    public ResponseEntity<?> getResumo() {

        Long clienteId = getClienteId();

        try {
            Carrinho carrinho = carrinhoService.buscarComItens(clienteId);

            Map<String, Object> response = montarResumo(carrinho);
            response.put("itens", carrinho.getItens());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}