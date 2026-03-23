package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.service.cliente.ClienteService;
import br.com.java.e_commerce.nexus.service.venda.CupomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/admin/cupons")
public class CupomController {

    private final CupomService cupomService;
    private final ClienteService clienteService;

    public CupomController(CupomService cupomService, ClienteService clienteService) {
        this.cupomService = cupomService;
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("cupons", cupomService.listarTodos());
        return "admin/cupons/lista";
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("cupom", new Cupom());
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("tipos", TipoCupom.values());
        return "admin/cupons/form";
    }

    @PostMapping("/salvar")
    public String salvar(@ModelAttribute Cupom cupom,
                         @RequestParam String dataValidadeStr,
                         RedirectAttributes redirectAttributes) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            cupom.setDataValidade(LocalDateTime.parse(dataValidadeStr, formatter));
            cupomService.salvar(cupom);
            redirectAttributes.addFlashAttribute("sucesso", "Cupom salvo com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao salvar cupom: " + e.getMessage());
        }
        return "redirect:/admin/cupons";
    }

    @PostMapping("/{id}/desativar")
    public String desativar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            cupomService.desativarCupom(id);
            redirectAttributes.addFlashAttribute("sucesso", "Cupom desativado com sucesso!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/cupons";
    }
}