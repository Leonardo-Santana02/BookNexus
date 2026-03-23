package br.com.java.e_commerce.nexus.service.venda;

import br.com.java.e_commerce.nexus.model.cliente.CartaoCredito;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.venda.Pagamento;
import br.com.java.e_commerce.nexus.model.venda.PagamentoCartao;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.model.enums.FormaPagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPagamento;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.repository.cliente.CartaoCreditoRepository;
import br.com.java.e_commerce.nexus.repository.venda.CupomRepository;
import br.com.java.e_commerce.nexus.repository.venda.PagamentoCartaoRepository;
import br.com.java.e_commerce.nexus.repository.venda.PagamentoRepository;
import br.com.java.e_commerce.nexus.repository.venda.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class PagamentoService {

    private static final BigDecimal VALOR_MINIMO_CARTAO = new BigDecimal("10.00");

    private final PagamentoRepository pagamentoRepository;
    private final PedidoRepository pedidoRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final CupomRepository cupomRepository;
    private final PagamentoCartaoRepository pagamentoCartaoRepository; // AGORA USADO!

    public PagamentoService(PagamentoRepository pagamentoRepository,
                            PedidoRepository pedidoRepository,
                            CartaoCreditoRepository cartaoCreditoRepository,
                            CupomRepository cupomRepository,
                            PagamentoCartaoRepository pagamentoCartaoRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.pedidoRepository = pedidoRepository;
        this.cartaoCreditoRepository = cartaoCreditoRepository;
        this.cupomRepository = cupomRepository;
        this.pagamentoCartaoRepository = pagamentoCartaoRepository;
    }

    // ===== DTOs internos =====

    public static class ValidacaoPrePagamento {
        public final BigDecimal totalCarrinho;
        public final BigDecimal totalCuponsTroca;
        public final BigDecimal totalCartoes;
        public final boolean possuiCupomCarrinho;
        public final BigDecimal totalAPagar;

        public ValidacaoPrePagamento(BigDecimal totalCarrinho, BigDecimal totalCuponsTroca,
                                     BigDecimal totalCartoes, boolean possuiCupomCarrinho) {
            this.totalCarrinho = totalCarrinho;
            this.totalCuponsTroca = totalCuponsTroca;
            this.totalCartoes = totalCartoes;
            this.possuiCupomCarrinho = possuiCupomCarrinho;
            this.totalAPagar = totalCarrinho.subtract(totalCuponsTroca).max(BigDecimal.ZERO);
        }
    }

    public static class ValidacaoPedido {
        public final BigDecimal totalPedido;
        public final BigDecimal totalDescontoCupons;
        public final BigDecimal totalAPagar;
        public final BigDecimal totalCartoes;

        public ValidacaoPedido(BigDecimal totalPedido, BigDecimal totalDescontoCupons,
                               BigDecimal totalAPagar, BigDecimal totalCartoes) {
            this.totalPedido = totalPedido;
            this.totalDescontoCupons = totalDescontoCupons;
            this.totalAPagar = totalAPagar;
            this.totalCartoes = totalCartoes;
        }
    }

    // ===== VALIDAÇÕES =====

    public ValidacaoPrePagamento validarPrePagamento(Long clienteId,
                                                     List<Long> cuponsIds,
                                                     Map<Long, BigDecimal> cartoesValores,
                                                     BigDecimal valorFrete) {
        // Esta validação será chamada antes de criar o pedido
        // Como não temos carrinho aqui, recebemos os valores diretamente
        BigDecimal totalCarrinho = BigDecimal.ZERO; // Será calculado no checkout

        boolean possuiCupomTrocaSelecionado = false;
        BigDecimal totalCuponsTroca = BigDecimal.ZERO;

        // Validar cupons de troca
        if (cuponsIds != null && !cuponsIds.isEmpty()) {
            for (Long cupomId : cuponsIds) {
                Cupom cupom = cupomRepository.findById(cupomId)
                        .orElseThrow(() -> new RuntimeException("Cupom não encontrado: " + cupomId));

                if (!cupom.isValido()) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " inválido ou expirado");
                }

                if (cupom.getCliente() == null || !cupom.getCliente().getId().equals(clienteId)) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " não pertence ao cliente");
                }

                if (cupom.getTipo() != TipoCupom.TROCA) {
                    throw new RuntimeException("Apenas cupons de TROCA podem ser usados no pagamento");
                }

                totalCuponsTroca = totalCuponsTroca.add(cupom.getValor());
                possuiCupomTrocaSelecionado = true;
            }
        }

        // Validar cartões
        BigDecimal totalCartoes = BigDecimal.ZERO;
        if (cartoesValores != null) {
            for (Map.Entry<Long, BigDecimal> entry : cartoesValores.entrySet()) {
                BigDecimal valor = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    // Regra: mínimo por cartão só se NÃO houver cupom de troca
                    if (!possuiCupomTrocaSelecionado && valor.compareTo(VALOR_MINIMO_CARTAO) < 0) {
                        throw new RuntimeException("Cada cartão deve pagar pelo menos R$ 10,00");
                    }

                    // Verificar se cartão existe
                    CartaoCredito cartao = cartaoCreditoRepository.findById(entry.getKey())
                            .orElseThrow(() -> new RuntimeException("Cartão não encontrado: " + entry.getKey()));

                    if (!cartao.getCliente().getId().equals(clienteId)) {
                        throw new RuntimeException("Cartão não pertence ao cliente");
                    }
                }
                totalCartoes = totalCartoes.add(valor);
            }
        }

        return new ValidacaoPrePagamento(totalCarrinho, totalCuponsTroca, totalCartoes, false);
    }

    public ValidacaoPedido validarPagamentoPedido(Long pedidoId,
                                                  List<Long> cuponsIds,
                                                  Map<Long, BigDecimal> cartoesValores) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() == StatusPedido.PAGO ||
                pedido.getStatus() == StatusPedido.ENVIADO ||
                pedido.getStatus() == StatusPedido.ENTREGUE) {
            throw new RuntimeException("Pedido não permite novo pagamento");
        }

        Long clienteId = pedido.getCliente().getId();

        // Validar cupons de troca
        BigDecimal totalDescontoCupons = BigDecimal.ZERO;
        boolean possuiCupomTrocaSelecionado = false;

        if (cuponsIds != null && !cuponsIds.isEmpty()) {
            for (Long cupomId : cuponsIds) {
                Cupom cupom = cupomRepository.findById(cupomId)
                        .orElseThrow(() -> new RuntimeException("Cupom não encontrado: " + cupomId));

                if (!cupom.isValido()) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " inválido ou expirado");
                }

                if (cupom.getCliente() == null || !cupom.getCliente().getId().equals(clienteId)) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " não pertence ao cliente");
                }

                if (cupom.getTipo() != TipoCupom.TROCA) {
                    throw new RuntimeException("Apenas cupons de TROCA podem ser usados no pagamento");
                }

                totalDescontoCupons = totalDescontoCupons.add(cupom.getValor());
                possuiCupomTrocaSelecionado = true;
            }
        }

        BigDecimal valorComDesconto = pedido.getValorTotal().subtract(totalDescontoCupons);
        if (valorComDesconto.compareTo(BigDecimal.ZERO) < 0) {
            valorComDesconto = BigDecimal.ZERO;
        }

        // Validar cartões
        BigDecimal totalCartoes = BigDecimal.ZERO;
        if (cartoesValores != null) {
            for (Map.Entry<Long, BigDecimal> entry : cartoesValores.entrySet()) {
                BigDecimal valor = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    if (!possuiCupomTrocaSelecionado && valor.compareTo(VALOR_MINIMO_CARTAO) < 0) {
                        throw new RuntimeException("Cada cartão deve pagar pelo menos R$ 10,00");
                    }

                    CartaoCredito cartao = cartaoCreditoRepository.findById(entry.getKey())
                            .orElseThrow(() -> new RuntimeException("Cartão não encontrado: " + entry.getKey()));

                    if (!cartao.getCliente().getId().equals(clienteId)) {
                        throw new RuntimeException("Cartão não pertence ao cliente");
                    }
                }
                totalCartoes = totalCartoes.add(valor);
            }
        }

        if (totalCartoes.compareTo(valorComDesconto) < 0) {
            throw new RuntimeException("Valor dos cartões insuficiente para cobrir o pedido");
        }

        return new ValidacaoPedido(pedido.getValorTotal(), totalDescontoCupons, valorComDesconto, totalCartoes);
    }

    // ===== PROCESSAMENTO =====

    @Transactional
    public Pagamento processarPagamento(Long pedidoId,
                                        List<Long> cuponsIds,
                                        Map<Long, BigDecimal> cartoesValores,
                                        Map<Long, Integer> cartoesParcelas) {

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        // Verificar se já existe pagamento
        Optional<Pagamento> pagamentoExistente = pagamentoRepository.findByPedidoId(pedidoId);
        Pagamento pagamento;

        if (pagamentoExistente.isPresent()) {
            pagamento = pagamentoExistente.get();
            // Limpar dados antigos
            if (pagamento.getCuponsUsados() != null) {
                pagamento.getCuponsUsados().clear();
            }
            if (pagamento.getCartoesUsados() != null) {
                // Remover cartões antigos do banco
                pagamentoCartaoRepository.deleteAll(pagamento.getCartoesUsados());
                pagamento.getCartoesUsados().clear();
            }
        } else {
            pagamento = new Pagamento();
            pagamento.setPedido(pedido);
        }

        pagamento.setFormaPagamento(FormaPagamento.CARTAO_CREDITO);
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pagamento.setDataPagamento(LocalDateTime.now());

        // Processar cupons
        BigDecimal totalDesconto = BigDecimal.ZERO;
        List<Cupom> cuponsReservados = new ArrayList<>();

        if (cuponsIds != null && !cuponsIds.isEmpty()) {
            for (Long cupomId : cuponsIds) {
                Cupom cupom = cupomRepository.findById(cupomId)
                        .orElseThrow(() -> new RuntimeException("Cupom não encontrado: " + cupomId));

                if (!cupom.isValido()) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " inválido");
                }

                // Reservar cupom (desativar temporariamente)
                cupom.setAtivo(false);
                cupomRepository.save(cupom);

                cuponsReservados.add(cupom);
                totalDesconto = totalDesconto.add(cupom.getValor());
            }

            pagamento.setCuponsUsados(cuponsReservados);

            // Resumo textual dos cupons
            String resumo = cuponsReservados.stream()
                    .map(Cupom::getCodigo)
                    .collect(Collectors.joining(", "));
            pagamento.setResumoCupons(resumo);
        }

        pagamento.setDesconto(totalDesconto);

        // Valor a ser pago com cartões
        BigDecimal valorComDesconto = pedido.getValorTotal().subtract(totalDesconto);
        if (valorComDesconto.compareTo(BigDecimal.ZERO) < 0) {
            valorComDesconto = BigDecimal.ZERO;
        }
        pagamento.setValor(valorComDesconto);

        // Processar cartões - AGORA USANDO PAGAMENTOCARTAO!
        BigDecimal totalCartoes = BigDecimal.ZERO;
        if (cartoesValores != null) {
            for (Map.Entry<Long, BigDecimal> entry : cartoesValores.entrySet()) {
                BigDecimal valor = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    CartaoCredito cartao = cartaoCreditoRepository.findById(entry.getKey())
                            .orElseThrow(() -> new RuntimeException("Cartão não encontrado: " + entry.getKey()));

                    int parcelas = 1;
                    if (cartoesParcelas != null && cartoesParcelas.containsKey(entry.getKey())) {
                        parcelas = cartoesParcelas.get(entry.getKey());
                    }

                    // CRIAR E SALVAR O PAGAMENTOCARTAO
                    PagamentoCartao pagamentoCartao = new PagamentoCartao();
                    pagamentoCartao.setPagamento(pagamento);
                    pagamentoCartao.setCartaoCredito(cartao);
                    pagamentoCartao.setValorPago(valor);
                    pagamentoCartao.setParcelas(parcelas);

                    // Adicionar à lista (o cascade vai salvar automaticamente)
                    pagamento.getCartoesUsados().add(pagamentoCartao);

                    totalCartoes = totalCartoes.add(valor);
                }
            }
        }

        // Verificar se o valor dos cartões é suficiente
        if (totalCartoes.compareTo(valorComDesconto) < 0) {
            throw new RuntimeException("Valor dos cartões insuficiente para cobrir o pedido");
        }

        pagamento = pagamentoRepository.save(pagamento);

        // Log para confirmar que os cartões foram salvos
        System.out.println("Pagamento salvo com ID: " + pagamento.getId());
        System.out.println("Cartões vinculados: " + pagamento.getCartoesUsados().size());

        pedido.setPagamento(pagamento);
        pedido.setStatus(StatusPedido.EM_ABERTO);
        pedidoRepository.save(pedido);

        return pagamento;
    }

    @Transactional
    public Pagamento confirmarPagamento(Long pagamentoId) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + pagamentoId));

        pagamento.aprovar();

        Pedido pedido = pagamento.getPedido();
        pedido.confirmarPagamento();
        pedidoRepository.save(pedido);

        // Calcular troco em cupom de troca, se houver
        BigDecimal valorPedido = pedido.getValorTotal();
        BigDecimal totalDesconto = pagamento.getDesconto();
        BigDecimal troco = totalDesconto.subtract(valorPedido);

        if (troco.compareTo(BigDecimal.ZERO) > 0) {
            try {
                Long clienteId = pedido.getCliente().getId();
                String descricao = "Troco do pagamento do pedido #" + pedido.getId();

                Cupom cupomTroco = new Cupom();
                cupomTroco.setCodigo("TROCO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                cupomTroco.setTipo(TipoCupom.TROCA);
                cupomTroco.setValor(troco);
                cupomTroco.setCliente(pedido.getCliente());
                cupomTroco.setDescricao(descricao);
                cupomTroco.setDataValidade(LocalDateTime.now().plusMonths(6));
                cupomTroco.setAtivo(true);
                cupomTroco.setMaximoUsos(1);

                cupomRepository.save(cupomTroco);
            } catch (Exception ignored) {
                // Não interrompe o fluxo se falhar a criação do cupom de troco
            }
        }

        // Consumir cupons usados (deletar ou apenas manter como inativos)
        List<Cupom> cuponsUsados = new ArrayList<>(pagamento.getCuponsUsados());
        pagamento.getCuponsUsados().clear();
        pagamentoRepository.save(pagamento); // Remove vínculo

        for (Cupom cupom : cuponsUsados) {
            try {
                cupomRepository.delete(cupom);
            } catch (Exception ex) {
                cupom.setAtivo(false);
                cupomRepository.save(cupom);
            }
        }

        return pagamentoRepository.save(pagamento);
    }

    @Transactional
    public Pagamento rejeitarPagamento(Long pagamentoId, String motivo) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + pagamentoId));

        pagamento.rejeitar(motivo);

        // Reativar cupons reservados
        if (pagamento.getCuponsUsados() != null) {
            for (Cupom cupom : pagamento.getCuponsUsados()) {
                cupom.setAtivo(true);
                cupomRepository.save(cupom);
            }
            pagamento.getCuponsUsados().clear();
        }

        Pedido pedido = pagamento.getPedido();
        pedido.cancelar();
        pedidoRepository.save(pedido);

        return pagamentoRepository.save(pagamento);
    }

    // ===== CONSULTAS =====

    public Optional<Pagamento> buscarPorId(Long id) {
        return pagamentoRepository.findById(id);
    }

    public Optional<Pagamento> buscarPorPedidoId(Long pedidoId) {
        return pagamentoRepository.findByPedidoId(pedidoId);
    }

    public List<Pagamento> listarTodos() {
        return pagamentoRepository.findAll();
    }

    public List<Pagamento> listarPorCliente(Long clienteId) {
        return pagamentoRepository.findByClienteId(clienteId);
    }

    public List<Pagamento> listarPorStatus(StatusPagamento status) {
        return pagamentoRepository.findByStatus(status);
    }

    public Pagamento salvar(Pagamento pagamento) {
        return pagamentoRepository.save(pagamento);
    }

    public boolean existePagamentoParaPedido(Long pedidoId) {
        return pagamentoRepository.existsByPedidoId(pedidoId);
    }
}