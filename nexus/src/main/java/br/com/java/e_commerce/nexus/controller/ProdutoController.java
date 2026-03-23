package br.com.java.e_commerce.nexus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProdutoController {

    // Lista de produtos
    @GetMapping("/produtos")
    public String listarProdutos() {
        return "admin/produtos/lista-produtos";
    }

    // Tela de novo produto
    @GetMapping("/produtos/novo")
    public String novoProduto() {
        return "admin/produtos/novo-produto";
    }

    // Tela de editar produto
    @GetMapping("/produtos/editar")
    public String editarProduto() {
        return "admin/produtos/editar-produto";
    }
}