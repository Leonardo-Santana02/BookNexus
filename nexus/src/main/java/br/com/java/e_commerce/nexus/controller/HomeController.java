package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.produto.Produto;
import br.com.java.e_commerce.nexus.repository.produto.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private ProdutoRepository produtoRepository;

    @GetMapping("/home")  // Mude de "/" para "/home"
    public String home(Model model) {
        // Buscar TODOS os produtos ativos do banco
        List<Produto> produtos = produtoRepository.findByAtivoTrue();

        // Adicionar a lista de produtos ao modelo
        model.addAttribute("produtos", produtos);

        // Aqui você pode adicionar o nome do cliente logado (quando tiver login)
        model.addAttribute("nomeCliente", "Visitante");

        return "cliente/home"; // Seu arquivo home.html deve estar em templates/home.html
    }
}