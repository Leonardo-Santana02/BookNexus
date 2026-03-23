package br.com.java.e_commerce.nexus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/")
    public String login() {
        return "auth/login";
    }

    // REMOVA este método ou comente
    // @GetMapping("/home")
    // public String home() {
    //     return "cliente/home";
    // }
}