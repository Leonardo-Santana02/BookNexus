package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.service.cliente.ClienteService;
import br.com.java.e_commerce.nexus.service.venda.CupomService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador responsável por gerenciar operações administrativas de cupons
 * e fornecer endpoints para consulta de cupons válidos para clientes.
 *
 * Este controlador possui duas responsabilidades principais:
 * 1. CRUD administrativo de cupons (acesso restrito a administradores)
 * 2. Endpoints REST para consulta de cupons válidos (usado pelo front-end)
 *
 * @Controller: Marca a classe como um controlador Spring MVC
 * @RequestMapping("/admin/cupons"): Todas as URLs deste controlador começam com "/admin/cupons"
 *
 * URLs disponíveis:
 * - GET  /admin/cupons                    → Listar todos os cupons (admin)
 * - GET  /admin/cupons/novo               → Formulário para criar novo cupom
 * - POST /admin/cupons/salvar             → Salvar cupom (criação/edição)
 * - POST /admin/cupons/{id}/desativar     → Desativar cupom
 * - GET  /admin/cupons/api/cliente/{id}/validos → Buscar cupons válidos para cliente (REST)
 *
 * IMPORTANTE: Em um sistema real com segurança, este controlador deveria ter
 * anotações como @PreAuthorize("hasRole('ADMIN')") para restringir acesso
 * administrativo apenas a usuários com perfil de administrador.
 */
@Controller
@RequestMapping("/admin/cupons")
public class CupomController {

    // Serviços injetados
    private final CupomService cupomService;     // Operações com cupons
    private final ClienteService clienteService; // Busca de clientes para associar cupons

    /**
     * Construtor para injeção de dependências.
     *
     * @param cupomService Serviço de cupons
     * @param clienteService Serviço de clientes (para listar clientes no formulário)
     */
    public CupomController(CupomService cupomService, ClienteService clienteService) {
        this.cupomService = cupomService;
        this.clienteService = clienteService;
    }

    // ===== ROTAS ADMINISTRATIVAS =====

    /**
     * Lista todos os cupons cadastrados no sistema.
     *
     * Esta é a página principal de gerenciamento de cupons para administradores.
     * Exibe uma tabela com todos os cupons, seus tipos, valores, validades e status.
     *
     * @param model Model do Spring para passar atributos para a view
     * @return Nome da template Thymeleaf/JSP a ser renderizada
     */
    @GetMapping
    public String listar(Model model) {
        // Busca todos os cupons do sistema (ativos e inativos)
        model.addAttribute("cupons", cupomService.listarTodos());
        return "admin/cupons/lista";
    }

    /**
     * Exibe o formulário para criar um novo cupom.
     *
     * O formulário permite:
     * - Definir código, tipo, valor, validade e descrição
     * - Associar o cupom a um cliente específico (opcional)
     * - Definir limite máximo de usos
     *
     * @param model Model do Spring para passar atributos para a view
     * @return Nome da template do formulário
     */
    @GetMapping("/novo")
    public String novoForm(Model model) {
        // Adiciona um novo objeto Cupom vazio para binding do formulário
        model.addAttribute("cupom", new Cupom());

        // Lista de clientes para poder associar o cupom a um cliente específico
        // Útil para cupons personalizados ou de troca
        model.addAttribute("clientes", clienteService.listarTodos());

        // Lista de tipos de cupom disponíveis (PROMOCIONAL, TROCA)
        model.addAttribute("tipos", TipoCupom.values());

        return "admin/cupons/form";
    }

    /**
     * Salva um cupom (criação ou atualização).
     *
     * Este endpoint recebe os dados do formulário e persiste o cupom no banco.
     *
     * @param cupom Objeto Cupom preenchido pelo formulário (via @ModelAttribute)
     * @param dataValidadeStr String com a data de validade no formato "yyyy-MM-dd HH:mm"
     * @param redirectAttributes Para mensagens flash após redirecionamento
     * @return Redirecionamento para a lista de cupons
     *
     * @example dataValidadeStr: "2025-12-31 23:59"
     */
    @PostMapping("/salvar")
    public String salvar(@ModelAttribute Cupom cupom,
                         @RequestParam String dataValidadeStr,
                         RedirectAttributes redirectAttributes) {
        try {
            // ===== CONVERSÃO DA DATA =====
            // O formulário envia a data como string, precisamos converter para LocalDateTime
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            cupom.setDataValidade(LocalDateTime.parse(dataValidadeStr, formatter));

            // ===== PERSISTÊNCIA =====
            // Se o cupom já tem ID, será atualizado; se não, será criado
            cupomService.salvar(cupom);

            // Mensagem de sucesso (será exibida na próxima requisição)
            redirectAttributes.addFlashAttribute("sucesso", "Cupom salvo com sucesso!");

        } catch (Exception e) {
            // Em caso de erro, exibe mensagem amigável
            redirectAttributes.addFlashAttribute("erro", "Erro ao salvar cupom: " + e.getMessage());
        }

        return "redirect:/admin/cupons";
    }

    /**
     * Desativa um cupom existente.
     *
     * Cupons desativados não podem mais ser utilizados pelos clientes.
     * Esta operação é útil para:
     * - Encerrar campanhas promocionais antes da data de validade
     * - Cancelar cupons problemáticos
     * - Remover cupons de troca que não devem mais ser usados
     *
     * @param id ID do cupom a ser desativado
     * @param redirectAttributes Para mensagens flash após redirecionamento
     * @return Redirecionamento para a lista de cupons
     */
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

    // ===== ENDPOINT REST PARA O FRONT-END (CARRINHO) =====

    /**
     * Endpoint REST que retorna todos os cupons válidos para um cliente.
     *
     * Este método é usado pelo front-end (JavaScript) para:
     * - Exibir cupons disponíveis na página do carrinho
     * - Validar se um cupom pode ser aplicado
     * - Mostrar cupons de troca disponíveis
     *
     * O serviço considera tanto cupons globais (associados a nenhum cliente)
     * quanto cupons exclusivos do cliente (como cupons de troca).
     *
     * @param clienteId ID do cliente para buscar cupons válidos
     * @return ResponseEntity com lista de mapas contendo dados dos cupons
     *
     * @example Resposta JSON:
     * [
     *   {
     *     "id": 1,
     *     "codigo": "BLACKFRIDAY",
     *     "tipo": "PROMOCIONAL",
     *     "valor": 20.00,
     *     "dataValidade": "2025-12-31T23:59:00",
     *     "descricao": "Desconto de R$20 em toda loja"
     *   },
     *   {
     *     "id": 2,
     *     "codigo": "TROCA-ABC123",
     *     "tipo": "TROCA",
     *     "valor": 50.00,
     *     "dataValidade": "2025-06-30T23:59:00",
     *     "descricao": "Crédito de troca do pedido #123"
     *   }
     * ]
     */
    @GetMapping("/api/cliente/{clienteId}/validos")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> listarCuponsValidosCliente(@PathVariable Long clienteId) {
        // ===== BUSCA CUPONS VÁLIDOS =====
        // O serviço retorna cupons que estão:
        // - Ativos (ativo = true)
        // - Não expirados (dataValidade > now)
        // - Com usos disponíveis (usosAtuais < maximoUsos)
        // - Disponíveis para o cliente (cliente == null OU cliente.id == clienteId)
        List<Cupom> cupons = cupomService.buscarCuponsValidosParaCliente(clienteId);

        // ===== CONVERTE PARA FORMATO JSON AMIGÁVEL =====
        // Converte a lista de Cupom (entidade JPA) para uma lista de Map
        // Isso evita problemas de serialização e permite controlar exatamente
        // quais campos são enviados ao front-end
        List<Map<String, Object>> response = cupons.stream().map(cupom -> {
            Map<String, Object> map = new HashMap<>();

            // Campos básicos do cupom
            map.put("id", cupom.getId());                       // ID interno
            map.put("codigo", cupom.getCodigo());               // Código promocional (ex: "BLACKFRIDAY")
            map.put("tipo", cupom.getTipo().toString());        // "PROMOCIONAL" ou "TROCA"
            map.put("valor", cupom.getValor());                 // Valor do desconto/crédito
            map.put("dataValidade", cupom.getDataValidade());   // Data de expiração
            map.put("descricao", cupom.getDescricao() != null ? cupom.getDescricao() : ""); // Descrição (evita null)

            return map;
        }).collect(Collectors.toList());

        // Retorna a lista com status HTTP 200 (OK)
        return ResponseEntity.ok(response);
    }
}