package br.com.java.e_commerce.nexus.service.venda;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import br.com.java.e_commerce.nexus.model.carrinho.ItemCarrinho;
import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.venda.Pagamento;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.enums.StatusPagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.cliente.EnderecoRepository;
import br.com.java.e_commerce.nexus.repository.venda.CupomRepository;
import br.com.java.e_commerce.nexus.repository.venda.PagamentoRepository;
import br.com.java.e_commerce.nexus.repository.venda.PedidoRepository;
import br.com.java.e_commerce.nexus.service.carrinho.CarrinhoService;
import br.com.java.e_commerce.nexus.service.exception.EstoqueInsuficienteException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final CarrinhoService carrinhoService;
    private final ClienteRepository clienteRepository;
    private final EnderecoRepository enderecoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final CupomRepository cupomRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         CarrinhoService carrinhoService,
                         ClienteRepository clienteRepository,
                         EnderecoRepository enderecoRepository,
                         PagamentoRepository pagamentoRepository,
                         CupomRepository cupomRepository) {
        this.pedidoRepository = pedidoRepository;
        this.carrinhoService = carrinhoService;
        this.clienteRepository = clienteRepository;
        this.enderecoRepository = enderecoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.cupomRepository = cupomRepository;
    }

    // ===== CRIAÇÃO DO PEDIDO =====

    @Transactional
    public Pedido criarPedidoDoCarrinho(Long clienteId, Long enderecoEntregaId) {
        Carrinho carrinho = carrinhoService.obterCarrinhoDoCliente(clienteId);

        if (carrinho.isEmpty()) {
            throw new RuntimeException("Carrinho está vazio");
        }

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        Endereco enderecoEntrega = enderecoRepository.findById(enderecoEntregaId)
                .orElseThrow(() -> new RuntimeException("Endereço de entrega não encontrado"));

        if (!enderecoEntrega.getCliente().getId().equals(clienteId)) {
            throw new RuntimeException("Endereço não pertence ao cliente");
        }

        // Validar estoque
        for (ItemCarrinho item : carrinho.getItens()) {
            if (!item.getProduto().temEstoque(item.getQuantidade())) {
                throw new EstoqueInsuficienteException(
                        "Produto " + item.getProduto().getTitulo() + " sem estoque suficiente"
                );
            }
        }

        // Criar pedido
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setEnderecoEntrega(enderecoEntrega);
        pedido.setItens(new ArrayList<>(carrinho.getItens()));
        pedido.setValorFrete(carrinho.getValorFrete());
        pedido.setDescontoPromocional(carrinho.getDescontoTotal());
        pedido.setResumoCuponsPromocionais(
                carrinho.getCupons().stream()
                        .filter(c -> c.getTipo() == TipoCupom.PROMOCIONAL)
                        .map(Cupom::getCodigo)
                        .collect(Collectors.joining(", "))
        );

        // Calcular totais
        pedido.setSubtotal(carrinho.getSubtotal());
        pedido.setValorTotal(carrinho.getTotal());
        pedido.setStatus(StatusPedido.EM_ABERTO);

        pedido = pedidoRepository.save(pedido);

        // Dar baixa no estoque
        for (ItemCarrinho item : carrinho.getItens()) {
            item.getProduto().baixarEstoque(item.getQuantidade());
        }

        // Processar cupons
        processarCuponsAposPedido(carrinho, pedido);

        // Limpar carrinho
        carrinhoService.limparCarrinho(clienteId);

        return pedido;
    }

    private void processarCuponsAposPedido(Carrinho carrinho, Pedido pedido) {
        List<Cupom> cuponsUsados = new ArrayList<>(carrinho.getCupons());

        for (Cupom cupom : cuponsUsados) {
            cupom.consumir();
            cupomRepository.save(cupom);

            // Se for cupom de troca, registrar no pagamento depois
            if (cupom.getTipo() == TipoCupom.TROCA) {
                // Será processado no pagamento
            }
        }
    }

    // ===== PAGAMENTO =====

    @Transactional
    public Pedido processarPagamento(Long pedidoId, Pagamento pagamento) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        if (pedido.getStatus() != StatusPedido.EM_ABERTO) {
            throw new RuntimeException("Pedido não está em aberto para pagamento");
        }

        pagamento.setPedido(pedido);
        pagamento.setValor(pedido.getValorTotal());
        pagamento.setDataPagamento(LocalDateTime.now());
        pagamento.setStatus(StatusPagamento.APROVADO);

        pagamentoRepository.save(pagamento);
        pedido.setPagamento(pagamento);
        pedido.confirmarPagamento();

        return pedidoRepository.save(pedido);
    }

    // ===== ATUALIZAÇÃO DE STATUS =====

    @Transactional
    public Pedido atualizarStatus(Long id, StatusPedido novoStatus) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        StatusPedido statusAtual = pedido.getStatus();

        // Validações de transição de status
        if (statusAtual == StatusPedido.CANCELADO) {
            throw new RuntimeException("Pedido cancelado não pode ter status alterado");
        }

        if (novoStatus == StatusPedido.ENVIADO && statusAtual != StatusPedido.PAGO) {
            throw new RuntimeException("Pedido precisa estar pago para ser enviado");
        }

        if (novoStatus == StatusPedido.ENTREGUE && statusAtual != StatusPedido.ENVIADO) {
            throw new RuntimeException("Pedido precisa estar enviado para ser entregue");
        }

        pedido.setStatus(novoStatus);

        switch (novoStatus) {
            case PAGO:
                pedido.setDataConfirmacao(LocalDateTime.now());
                break;
            case ENVIADO:
                pedido.setDataEnvio(LocalDateTime.now());
                pedido.setCodigoRastreio(gerarCodigoRastreio());
                break;
            case ENTREGUE:
                pedido.setDataEntrega(LocalDateTime.now());
                break;
        }

        return pedidoRepository.save(pedido);
    }

    private String gerarCodigoRastreio() {
        return "BR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional
    public Pedido cancelarPedido(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        if (pedido.getStatus() == StatusPedido.ENTREGUE) {
            throw new RuntimeException("Pedido já entregue não pode ser cancelado");
        }

        if (pedido.getStatus() == StatusPedido.ENVIADO) {
            throw new RuntimeException("Pedido já enviado não pode ser cancelado");
        }

        pedido.cancelar();

        // Estornar estoque se necessário
        if (pedido.getStatus() == StatusPedido.PAGO) {
            for (ItemCarrinho item : pedido.getItens()) {
                item.getProduto().reporEstoque(item.getQuantidade());
            }
        }

        return pedidoRepository.save(pedido);
    }

    // ===== DEVOLUÇÃO =====

    @Transactional
    public Cupom confirmarDevolucaoEGerarCupom(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        if (pedido.getStatus() != StatusPedido.AGUARDANDO_DEVOLUCAO) {
            throw new RuntimeException("O pedido não está aguardando devolução");
        }

        // Atualizar status do pedido
        pedido.setStatus(StatusPedido.DEVOLUCAO_CONFIRMADA);
        pedidoRepository.save(pedido);

        // Estornar estoque
        for (ItemCarrinho item : pedido.getItens()) {
            item.getProduto().reporEstoque(item.getQuantidade());
        }

        // Criar cupom de troca
        Cupom cupom = new Cupom();
        cupom.setCodigo("TROCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        cupom.setTipo(TipoCupom.TROCA);
        cupom.setValor(pedido.getValorTotal());
        cupom.setCliente(pedido.getCliente());
        cupom.setDescricao("Cupom de troca referente ao pedido #" + pedido.getId());
        cupom.setDataValidade(LocalDateTime.now().plusMonths(6));
        cupom.setAtivo(true);
        cupom.setMaximoUsos(1);

        return cupomRepository.save(cupom);
    }

    // ===== CONSULTAS =====

    @Transactional(readOnly = true)
    public Optional<Pedido> buscarPorId(Long id) {
        return pedidoRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarPorCliente(Long clienteId) {
        return pedidoRepository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarPorStatus(StatusPedido status) {
        return pedidoRepository.findByStatus(status);
    }

    // ===== RELATÓRIOS =====

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> calcularVendasPorCategoria(LocalDateTime inicio, LocalDateTime fim) {
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);
        Map<String, BigDecimal> vendasPorCategoria = new HashMap<>();

        for (Pedido pedido : pedidos) {
            for (ItemCarrinho item : pedido.getItens()) {
                String categoria = item.getProduto().getGenero();
                BigDecimal total = item.getProduto().getPreco()
                        .multiply(BigDecimal.valueOf(item.getQuantidade()));
                vendasPorCategoria.merge(categoria, total, BigDecimal::add);
            }
        }
        return vendasPorCategoria;
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> calcularVendasPorProduto(LocalDateTime inicio, LocalDateTime fim) {
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);
        Map<String, Integer> vendasPorProduto = new HashMap<>();

        for (Pedido pedido : pedidos) {
            for (ItemCarrinho item : pedido.getItens()) {
                String titulo = item.getProduto().getTitulo();
                vendasPorProduto.merge(titulo, item.getQuantidade(), Integer::sum);
            }
        }
        return vendasPorProduto;
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularTotalVendas(LocalDateTime inicio, LocalDateTime fim) {
        return pedidoRepository.findVendasPorPeriodo(inicio, fim).stream()
                .map(Pedido::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public long contarVendas(LocalDateTime inicio, LocalDateTime fim) {
        return pedidoRepository.findVendasPorPeriodo(inicio, fim).size();
    }
}