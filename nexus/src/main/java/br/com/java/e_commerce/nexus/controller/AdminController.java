package br.com.java.e_commerce.nexus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    // ✅ ADICIONAR ESTE MÉTODO - Página inicial do admin
    @GetMapping
    public String home() {
        return "admin/admin-home"; // Seu arquivo admin-home.html deve estar em templates/admin/home.html
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "admin/dashboard/dashboard";
    }

    @GetMapping("/log")
    public String log() {
        return "admin/log/log";
    }

    @GetMapping("/pedidos")
    public String pedidosAdmin() {
        return "admin/pedidos/lista-pedidos";
    }

    @GetMapping("/produtos")
    public String listaProdutos() {
        return "admin/produtos/lista-produtos";
    }

}