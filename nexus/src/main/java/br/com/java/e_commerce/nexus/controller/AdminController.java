package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.service.venda.AnaliseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AnaliseService analiseService;
    private final ObjectMapper objectMapper;

    // Injeção de dependência via construtor
    public AdminController(AnaliseService analiseService) {
        this.analiseService = analiseService;
        this.objectMapper = new ObjectMapper();
    }

    // Página inicial do admin
    @GetMapping
    public String home() {
        return "admin/admin-home";
    }

    /**
     * Dashboard com gráficos dinâmicos
     * @param dataInicio Data inicial do filtro (opcional)
     * @param dataFim Data final do filtro (opcional)
     * @param tipo Tipo de análise: "categoria" ou "produto"
     * @param model Model para envio de dados à view
     * @return Nome do template
     */
    @GetMapping("/dashboard")
    public String dashboard(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
            @RequestParam(required = false, defaultValue = "categoria") String tipo,
            Model model) {

        // Define datas padrão (últimos 30 dias)
        if (dataInicio == null) {
            dataInicio = LocalDate.now().minusDays(30);
        }
        if (dataFim == null) {
            dataFim = LocalDate.now();
        }

        // Converte para LocalDateTime (início do dia / fim do dia)
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.atTime(23, 59, 59);

        // Log para debug
        System.out.println("=== DASHBOARD DEBUG ===");
        System.out.println("Período: " + inicio + " até " + fim);
        System.out.println("Tipo: " + tipo);

        // 1. Dados para o gráfico (séries temporais)
        Map<String, Object> dadosGrafico;
        String tituloGrafico;
        String labelY;

        try {
            if ("produto".equalsIgnoreCase(tipo)) {
                dadosGrafico = analiseService.gerarSeriesVolumePorProduto(inicio, fim);
                tituloGrafico = "📈 Volume de Vendas por Produto";
                labelY = "Unidades Vendidas";
            } else {
                dadosGrafico = analiseService.gerarSeriesPorCategoria(inicio, fim);
                tituloGrafico = "💰 Vendas por Gênero Literário";
                labelY = "Valor (R$)";
            }
        } catch (Exception e) {
            System.err.println("Erro ao obter dados do gráfico: " + e.getMessage());
            dadosGrafico = new HashMap<>();
            dadosGrafico.put("labels", new ArrayList<>());
            dadosGrafico.put("datasets", new ArrayList<>());
            tituloGrafico = "💰 Vendas por Período";
            labelY = "Valor (R$)";
        }

        // 2. Métricas do dashboard
        BigDecimal totalVendas = BigDecimal.ZERO;
        Long totalPedidos = 0L;
        Long totalItensVendidos = 0L;

        try {
            totalVendas = analiseService.calcularTotalVendas(inicio, fim);
            totalPedidos = analiseService.contarVendas(inicio, fim);
            totalItensVendidos = analiseService.calcularTotalItensVendidos(inicio, fim);
        } catch (Exception e) {
            System.err.println("Erro ao obter métricas: " + e.getMessage());
        }

        // Calcula ticket médio (evita divisão por zero)
        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (totalPedidos != null && totalPedidos > 0 && totalVendas != null) {
            ticketMedio = totalVendas.divide(BigDecimal.valueOf(totalPedidos), 2, RoundingMode.HALF_UP);
        }

        // 3. Converter dados para JSON para usar no JavaScript
        String labelsJson = "[]";
        String datasetsJson = "[]";

        try {
            Object labels = dadosGrafico.get("labels");
            Object datasets = dadosGrafico.get("datasets");

            labelsJson = objectMapper.writeValueAsString(labels != null ? labels : new ArrayList<>());
            datasetsJson = objectMapper.writeValueAsString(datasets != null ? datasets : new ArrayList<>());

            System.out.println("Labels convertidos para JSON com sucesso");
            System.out.println("Datasets convertidos para JSON com sucesso");
        } catch (Exception e) {
            System.err.println("Erro ao converter dados para JSON: " + e.getMessage());
            labelsJson = "[]";
            datasetsJson = "[]";
        }

        // 4. Formata as métricas para exibição
        String totalVendasFormatado = formatCurrency(totalVendas);
        String ticketMedioFormatado = formatCurrency(ticketMedio);

        // Formata as datas para exibição no filtro
        String dataInicioStr = dataInicio.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String dataFimStr = dataFim.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 5. Adiciona atributos ao modelo
        model.addAttribute("labelsJson", labelsJson);
        model.addAttribute("datasetsJson", datasetsJson);
        model.addAttribute("tituloGrafico", tituloGrafico);
        model.addAttribute("labelY", labelY);
        model.addAttribute("dataInicio", dataInicio);
        model.addAttribute("dataFim", dataFim);
        model.addAttribute("dataInicioStr", dataInicioStr);
        model.addAttribute("dataFimStr", dataFimStr);
        model.addAttribute("tipo", tipo);
        model.addAttribute("totalVendas", totalVendasFormatado);
        model.addAttribute("ticketMedio", ticketMedioFormatado);
        model.addAttribute("totalPedidos", totalPedidos != null ? totalPedidos : 0L);
        model.addAttribute("totalItensVendidos", totalItensVendidos != null ? totalItensVendidos : 0L);

        // Dados adicionais para debug (opcional, pode ser removido em produção)
        model.addAttribute("temDados", labelsJson.length() > 4 && datasetsJson.length() > 4);

        // Log final
        System.out.println("Total de Vendas: " + totalVendasFormatado);
        System.out.println("Total de Pedidos: " + totalPedidos);
        System.out.println("Ticket Médio: " + ticketMedioFormatado);
        System.out.println("Total de Itens Vendidos: " + totalItensVendidos);
        System.out.println("================================");

        return "admin/dashboard/dashboard";
    }

    /**
     * Formata um BigDecimal para moeda brasileira (R$)
     */
    private String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "R$ 0,00";
        }
        return String.format("R$ %,.2f", value)
                .replace(",", "v")
                .replace(".", ",")
                .replace("v", ".");
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