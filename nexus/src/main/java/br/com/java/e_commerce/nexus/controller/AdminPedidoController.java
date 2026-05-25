package br.com.java.e_commerce.nexus.controller;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import br.com.java.e_commerce.nexus.model.venda.ItemPedido;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.service.venda.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/api/pedidos")
public class AdminPedidoController {

    private final PedidoService pedidoService;
    private final ClienteRepository clienteRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public AdminPedidoController(PedidoService pedidoService, ClienteRepository clienteRepository) {
        this.pedidoService = pedidoService;
        this.clienteRepository = clienteRepository;
    }

    /**
     * Lista todos os clientes para o dropdown (apenas id, nome e email)
     */
    @GetMapping("/clientes")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> listarClientes() {
        List<Cliente> clientes = clienteRepository.findByInativadoFalse(); // Apenas clientes ativos

        List<Map<String, Object>> result = clientes.stream()
                .map(cliente -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cliente.getId());
                    map.put("nome", cliente.getNome());
                    map.put("email", cliente.getEmail());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Lista todos os pedidos de um cliente específico
     */
    @GetMapping("/cliente/{clienteId}")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> listarPedidosPorCliente(@PathVariable Long clienteId) {
        List<Pedido> pedidos = pedidoService.listarPorCliente(clienteId);

        List<Map<String, Object>> result = pedidos.stream()
                .map(pedido -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", pedido.getId());
                    map.put("clienteId", pedido.getCliente().getId());
                    map.put("clienteNome", pedido.getCliente().getNome());
                    map.put("dataCriacao", pedido.getDataCriacao().format(DATE_FORMATTER));
                    map.put("valorTotal", pedido.getValorTotal());
                    map.put("status", pedido.getStatus().name());
                    map.put("statusDescricao", pedido.getStatus().getDescricao());
                    map.put("quantidadeItens", pedido.getQuantidadeTotalItens());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Retorna detalhes completos de um pedido (incluindo itens)
     */
    @GetMapping("/{pedidoId}/detalhes")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> detalhesPedido(@PathVariable Long pedidoId) {
        Pedido pedido = pedidoService.buscarPorId(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", pedido.getId());
        response.put("dataCriacao", pedido.getDataCriacao().format(DATE_TIME_FORMATTER));
        response.put("status", pedido.getStatus().name());
        response.put("statusDescricao", pedido.getStatus().getDescricao());
        response.put("valorTotal", pedido.getValorTotal());
        response.put("subtotal", pedido.getSubtotal());
        response.put("descontoPromocional", pedido.getDescontoPromocional());
        response.put("valorFrete", pedido.getValorFrete());
        response.put("codigoRastreio", pedido.getCodigoRastreio() != null ? pedido.getCodigoRastreio() : "Não informado");

        // Informações do cliente
        Cliente cliente = pedido.getCliente();
        Map<String, Object> clienteInfo = new HashMap<>();
        clienteInfo.put("id", cliente.getId());
        clienteInfo.put("nome", cliente.getNome());
        clienteInfo.put("email", cliente.getEmail());
        clienteInfo.put("cpf", cliente.getCpf());
        response.put("cliente", clienteInfo);

        // Endereço de entrega
        if (pedido.getEnderecoEntrega() != null) {
            String enderecoStr = String.format("%s %s, %s - %s, %s - %s",
                    pedido.getEnderecoEntrega().getTipoLogradouro() != null ? pedido.getEnderecoEntrega().getTipoLogradouro().getDescricao() : "",
                    pedido.getEnderecoEntrega().getRua() != null ? pedido.getEnderecoEntrega().getRua() : "",
                    pedido.getEnderecoEntrega().getNumero() != null ? pedido.getEnderecoEntrega().getNumero() : "",
                    pedido.getEnderecoEntrega().getBairro() != null ? pedido.getEnderecoEntrega().getBairro() : "",
                    pedido.getEnderecoEntrega().getCidade() != null ? pedido.getEnderecoEntrega().getCidade() : "",
                    pedido.getEnderecoEntrega().getUf() != null ? pedido.getEnderecoEntrega().getUf().getSigla() : ""
            );
            response.put("enderecoEntrega", enderecoStr);
        } else {
            response.put("enderecoEntrega", "Endereço não informado");
        }

        // Itens do pedido
        List<Map<String, Object>> itens = new ArrayList<>();
        for (ItemPedido item : pedido.getItens()) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("produtoId", item.getProduto().getId());
            itemMap.put("produtoTitulo", item.getProduto().getTitulo());
            itemMap.put("produtoAutor", item.getProduto().getAutor());
            itemMap.put("quantidade", item.getQuantidade());
            itemMap.put("precoUnitario", item.getPrecoUnitario());
            itemMap.put("subtotalItem", item.getPrecoTotal());
            itens.add(itemMap);
        }
        response.put("itens", itens);

        // Datas adicionais
        if (pedido.getDataConfirmacao() != null) {
            response.put("dataConfirmacao", pedido.getDataConfirmacao().format(DATE_TIME_FORMATTER));
        }
        if (pedido.getDataEnvio() != null) {
            response.put("dataEnvio", pedido.getDataEnvio().format(DATE_TIME_FORMATTER));
        }
        if (pedido.getDataEntrega() != null) {
            response.put("dataEntrega", pedido.getDataEntrega().format(DATE_TIME_FORMATTER));
        }

        // Informações de pagamento
        if (pedido.getPagamento() != null) {
            Map<String, Object> pagamentoInfo = new HashMap<>();
            pagamentoInfo.put("forma", pedido.getPagamento().getFormaPagamento() != null ?
                    pedido.getPagamento().getFormaPagamento().getDescricao() : "Não informado");
            pagamentoInfo.put("status", pedido.getPagamento().getStatus() != null ?
                    pedido.getPagamento().getStatus() : "Não processado");
            pagamentoInfo.put("data", pedido.getPagamento().getDataPagamento() != null ?
                    pedido.getPagamento().getDataPagamento().format(DATE_TIME_FORMATTER) : "Não processado");
            response.put("pagamento", pagamentoInfo);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Atualiza o status de um pedido
     */
    @PutMapping("/{pedidoId}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> atualizarStatus(@PathVariable Long pedidoId,
                                                               @RequestBody Map<String, String> dados) {
        try {
            String novoStatus = dados.get("status");
            StatusPedido status = StatusPedido.valueOf(novoStatus);

            pedidoService.atualizarStatus(pedidoId, status);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("mensagem", "Status atualizado com sucesso");
            response.put("novoStatus", status.name());
            response.put("novoStatusDescricao", status.getDescricao());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("sucesso", false);
            error.put("erro", "Status inválido: " + dados.get("status"));
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("sucesso", false);
            error.put("erro", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}