package br.com.java.e_commerce.nexus.service.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.enums.MotivoDevolucao;
import br.com.java.e_commerce.nexus.model.enums.StatusSolicitacao;
import br.com.java.e_commerce.nexus.model.enums.StatusPedido;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.model.venda.*;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.produto.ProdutoRepository;
import br.com.java.e_commerce.nexus.repository.venda.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class DevolucaoService {

    private final SolicitacaoDevolucaoRepository solicitacaoRepository;
    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final CupomRepository cupomRepository;
    private final ProdutoRepository produtoRepository;

    public DevolucaoService(SolicitacaoDevolucaoRepository solicitacaoRepository,
                            PedidoRepository pedidoRepository,
                            ClienteRepository clienteRepository,
                            ItemPedidoRepository itemPedidoRepository,
                            CupomRepository cupomRepository,
                            ProdutoRepository produtoRepository) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.cupomRepository = cupomRepository;
        this.produtoRepository = produtoRepository;
    }

    // ===== SOLICITAÇÕES =====

    /**
     * Calcula a parcela proporcional do frete para os itens selecionados
     */
    private BigDecimal calcularParcelaFrete(Pedido pedido, List<ItemPedido> itensSelecionados) {
        if (pedido.getValorFrete() == null || pedido.getValorFrete().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calcula o subtotal total do pedido
        BigDecimal subtotalTotal = pedido.getSubtotal();

        // Calcula o subtotal dos itens selecionados
        BigDecimal subtotalSelecionados = itensSelecionados.stream()
                .map(ItemPedido::getPrecoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Se não há itens selecionados ou subtotal total é zero, retorna zero
        if (subtotalTotal.compareTo(BigDecimal.ZERO) <= 0 || subtotalSelecionados.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calcula a parcela proporcional do frete
        // Fórmula: (frete_total * subtotal_selecionados) / subtotal_total
        return pedido.getValorFrete()
                .multiply(subtotalSelecionados)
                .divide(subtotalTotal, 2, RoundingMode.HALF_EVEN);
    }

    /**
     * Solicita devolução de itens específicos de um pedido
     * Inclui cálculo proporcional do frete
     */
    @Transactional
    public SolicitacaoDevolucao solicitarDevolucao(Long pedidoId,
                                                   Long clienteId,
                                                   List<Long> itensPedidoIds,
                                                   MotivoDevolucao motivo,
                                                   String justificativa) {

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        // Valida se o pedido pertence ao cliente
        if (!pedido.getCliente().getId().equals(clienteId)) {
            throw new RuntimeException("Este pedido não pertence ao cliente informado");
        }

        // Valida se o pedido foi entregue
        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new RuntimeException("A devolução só pode ser solicitada para pedidos entregues");
        }

        // Verifica se já existe solicitação pendente para este pedido
        List<StatusSolicitacao> statusPendentes = List.of(
                StatusSolicitacao.PENDENTE,
                StatusSolicitacao.APROVADA,
                StatusSolicitacao.AGUARDANDO_RECEBIMENTO
        );
        if (solicitacaoRepository.existsByPedidoAndStatusIn(pedido, statusPendentes)) {
            throw new RuntimeException("Já existe uma solicitação de devolução em andamento para este pedido");
        }

        // Busca os itens do pedido
        List<ItemPedido> itensPedido = itemPedidoRepository.findAllById(itensPedidoIds);

        if (itensPedido.isEmpty()) {
            throw new RuntimeException("Nenhum item válido selecionado para devolução");
        }

        // VALIDA se algum item já foi devolvido em solicitação anterior
        for (ItemPedido itemPedido : itensPedido) {
            if (solicitacaoRepository.existsItemDevolvido(itemPedido.getId())) {
                throw new RuntimeException("O item '" + itemPedido.getProduto().getTitulo() +
                        "' já foi devolvido em uma solicitação anterior");
            }
        }

        // Calcula o valor total dos itens selecionados
        BigDecimal valorTotalItens = itensPedido.stream()
                .map(ItemPedido::getPrecoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcula a parcela proporcional do frete para os itens selecionados
        BigDecimal parcelaFrete = calcularParcelaFrete(pedido, itensPedido);

        // Valor total da devolução = itens + frete proporcional
        BigDecimal valorTotalComFrete = valorTotalItens.add(parcelaFrete);

        // Cria a solicitação
        SolicitacaoDevolucao solicitacao = new SolicitacaoDevolucao();
        solicitacao.setPedido(pedido);
        solicitacao.setCliente(pedido.getCliente());
        solicitacao.setValorSolicitado(valorTotalComFrete); // Inclui frete proporcional
        solicitacao.setMotivo(motivo);
        solicitacao.setJustificativa(justificativa);
        solicitacao.setStatus(StatusSolicitacao.PENDENTE);

        // Cria os itens de devolução
        for (ItemPedido itemPedido : itensPedido) {
            ItemDevolucao itemDevolucao = new ItemDevolucao();
            itemDevolucao.setItemPedido(itemPedido);
            itemDevolucao.setQuantidade(itemPedido.getQuantidade());
            itemDevolucao.setValorUnitarioDevolvido(itemPedido.getPrecoUnitario());
            itemDevolucao.setSolicitacao(solicitacao);
            solicitacao.getItensDevolvidos().add(itemDevolucao);
        }

        solicitacao = solicitacaoRepository.save(solicitacao);

        // Atualiza status do pedido
        pedido.setStatus(StatusPedido.AGUARDANDO_DEVOLUCAO);
        pedidoRepository.save(pedido);

        return solicitacao;
    }

    /**
     * Solicita devolução do pedido completo
     * Inclui o frete total
     */
    @Transactional
    public SolicitacaoDevolucao solicitarDevolucaoTotal(Long pedidoId,
                                                        Long clienteId,
                                                        MotivoDevolucao motivo,
                                                        String justificativa) {

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        if (!pedido.getCliente().getId().equals(clienteId)) {
            throw new RuntimeException("Este pedido não pertence ao cliente informado");
        }

        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new RuntimeException("A devolução só pode ser solicitada para pedidos entregues");
        }

        List<StatusSolicitacao> statusPendentes = List.of(
                StatusSolicitacao.PENDENTE,
                StatusSolicitacao.APROVADA,
                StatusSolicitacao.AGUARDANDO_RECEBIMENTO
        );
        if (solicitacaoRepository.existsByPedidoAndStatusIn(pedido, statusPendentes)) {
            throw new RuntimeException("Já existe uma solicitação de devolução em andamento");
        }

        // VALIDA itens do pedido completo
        for (ItemPedido itemPedido : pedido.getItens()) {
            if (solicitacaoRepository.existsItemDevolvido(itemPedido.getId())) {
                throw new RuntimeException("O item '" + itemPedido.getProduto().getTitulo() +
                        "' já foi devolvido em uma solicitação anterior");
            }
        }

        // O valor total do pedido (pedido.getValorTotal()) já inclui o frete
        // Fórmula: valorTotal = subtotal - desconto + frete
        BigDecimal valorTotalComFrete = pedido.getValorTotal();

        // Cria solicitação com todos os itens e valor total (incluindo frete)
        SolicitacaoDevolucao solicitacao = new SolicitacaoDevolucao();
        solicitacao.setPedido(pedido);
        solicitacao.setCliente(pedido.getCliente());
        solicitacao.setValorSolicitado(valorTotalComFrete); // Já inclui o frete!
        solicitacao.setMotivo(motivo);
        solicitacao.setJustificativa(justificativa);
        solicitacao.setStatus(StatusSolicitacao.PENDENTE);

        for (ItemPedido itemPedido : pedido.getItens()) {
            ItemDevolucao itemDevolucao = new ItemDevolucao();
            itemDevolucao.setItemPedido(itemPedido);
            itemDevolucao.setQuantidade(itemPedido.getQuantidade());
            itemDevolucao.setValorUnitarioDevolvido(itemPedido.getPrecoUnitario());
            itemDevolucao.setSolicitacao(solicitacao);
            solicitacao.getItensDevolvidos().add(itemDevolucao);
        }

        solicitacao = solicitacaoRepository.save(solicitacao);

        pedido.setStatus(StatusPedido.AGUARDANDO_DEVOLUCAO);
        pedidoRepository.save(pedido);

        return solicitacao;
    }

    // ===== VALIDAÇÃO DE PRAZO (MANTIDA MAS NÃO USADA) =====
    @Deprecated
    private boolean validarPrazoDevolucao(Pedido pedido, MotivoDevolucao motivo) {
        return true;
    }

    // ===== APROVAÇÃO E PROCESSAMENTO =====

    @Transactional
    public SolicitacaoDevolucao aprovarSolicitacao(Long solicitacaoId, BigDecimal valorAprovado, String observacaoAdmin) {
        SolicitacaoDevolucao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RuntimeException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new RuntimeException("Apenas solicitações pendentes podem ser aprovadas");
        }

        BigDecimal valorAprovadoFinal = valorAprovado != null && valorAprovado.compareTo(BigDecimal.ZERO) > 0
                ? valorAprovado.min(solicitacao.getValorSolicitado())
                : solicitacao.getValorSolicitado();

        solicitacao.setValorAprovado(valorAprovadoFinal);
        solicitacao.aprovar(observacaoAdmin);
        solicitacao.setStatus(StatusSolicitacao.APROVADA);

        return solicitacaoRepository.save(solicitacao);
    }

    @Transactional
    public SolicitacaoDevolucao recusarSolicitacao(Long solicitacaoId, String motivoRecusa, String observacaoAdmin) {
        SolicitacaoDevolucao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RuntimeException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new RuntimeException("Apenas solicitações pendentes podem ser recusadas");
        }

        solicitacao.recusar(motivoRecusa);
        if (observacaoAdmin != null) {
            solicitacao.setObservacaoAdmin(observacaoAdmin);
        }

        Pedido pedido = solicitacao.getPedido();
        pedido.setStatus(StatusPedido.ENTREGUE);
        pedidoRepository.save(pedido);

        return solicitacaoRepository.save(solicitacao);
    }

    @Transactional
    public SolicitacaoDevolucao confirmarRecebimentoProdutos(Long solicitacaoId) {
        SolicitacaoDevolucao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RuntimeException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.APROVADA) {
            throw new RuntimeException("Apenas solicitações aprovadas podem ter recebimento confirmado");
        }

        solicitacao.confirmarRecebimento();

        for (ItemDevolucao item : solicitacao.getItensDevolvidos()) {
            var produto = item.getItemPedido().getProduto();
            produto.reporEstoque(item.getQuantidade());
            produtoRepository.save(produto);
        }

        return solicitacaoRepository.save(solicitacao);
    }

    @Transactional
    public Cupom concluirDevolucaoComCupom(Long solicitacaoId) {
        SolicitacaoDevolucao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RuntimeException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.RECEBIDA) {
            throw new RuntimeException("Produto precisa ser recebido antes de concluir a devolução");
        }

        // O valor do reembolso já inclui o frete (proporcional ou total)
        BigDecimal valorReembolso = solicitacao.getValorAprovado() != null
                ? solicitacao.getValorAprovado()
                : solicitacao.getValorSolicitado();

        // Gera cupom de troca com o valor total (produtos + frete)
        Cupom cupom = new Cupom();
        cupom.setCodigo("TROCA-" + System.currentTimeMillis() + "-" + solicitacaoId);
        cupom.setTipo(TipoCupom.TROCA);
        cupom.setValor(valorReembolso);
        cupom.setCliente(solicitacao.getCliente());
        cupom.setDescricao("Troca referente à solicitação #" + solicitacaoId + " (Valor total com frete)");
        cupom.setDataValidade(LocalDateTime.now().plusMonths(6));
        cupom.setAtivo(true);
        cupom.setMaximoUsos(1);

        cupom = cupomRepository.save(cupom);

        solicitacao.concluir();

        // Atualiza status do pedido para DEVOLUCAO_CONFIRMADA
        Pedido pedido = solicitacao.getPedido();
        pedido.setStatus(StatusPedido.DEVOLUCAO_CONFIRMADA);
        pedidoRepository.save(pedido);

        solicitacaoRepository.save(solicitacao);

        return cupom;
    }

    @Transactional
    public SolicitacaoDevolucao cancelarSolicitacao(Long solicitacaoId, Long clienteId) {
        SolicitacaoDevolucao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(() -> new RuntimeException("Solicitação não encontrada"));

        if (!solicitacao.getCliente().getId().equals(clienteId)) {
            throw new RuntimeException("Esta solicitação não pertence a este cliente");
        }

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE &&
                solicitacao.getStatus() != StatusSolicitacao.APROVADA) {
            throw new RuntimeException("Apenas solicitações pendentes ou aprovadas podem ser canceladas");
        }

        solicitacao.cancelar();

        Pedido pedido = solicitacao.getPedido();
        pedido.setStatus(StatusPedido.ENTREGUE);
        pedidoRepository.save(pedido);

        return solicitacaoRepository.save(solicitacao);
    }

    // ===== CONSULTAS =====

    public List<SolicitacaoDevolucao> listarTodas() {
        return solicitacaoRepository.findAll();
    }

    public List<SolicitacaoDevolucao> listarPorCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        return solicitacaoRepository.findByClienteOrderByDataSolicitacaoDesc(cliente);
    }

    public List<SolicitacaoDevolucao> listarPorStatus(StatusSolicitacao status) {
        return solicitacaoRepository.findByStatus(status);
    }

    public SolicitacaoDevolucao buscarPorId(Long id) {
        return solicitacaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitação não encontrada"));
    }

    public Optional<SolicitacaoDevolucao> buscarUltimaPorPedido(Pedido pedido) {
        return solicitacaoRepository.findFirstByPedidoOrderByDataSolicitacaoDesc(pedido);
    }

    /**
     * Verifica se um pedido possui solicitação de devolução pendente
     */
    public boolean possuiSolicitacaoPendente(Pedido pedido) {
        List<StatusSolicitacao> statusPendentes = List.of(
                StatusSolicitacao.PENDENTE,
                StatusSolicitacao.APROVADA,
                StatusSolicitacao.AGUARDANDO_RECEBIMENTO
        );
        return solicitacaoRepository.existsByPedidoAndStatusIn(pedido, statusPendentes);
    }

    /**
     * Verifica se um pedido possui solicitação de devolução em andamento
     */
    public boolean possuiSolicitacaoEmAndamento(Pedido pedido) {
        List<StatusSolicitacao> statusEmAndamento = List.of(
                StatusSolicitacao.PENDENTE,
                StatusSolicitacao.APROVADA,
                StatusSolicitacao.AGUARDANDO_RECEBIMENTO,
                StatusSolicitacao.RECEBIDA
        );
        return solicitacaoRepository.existsByPedidoAndStatusIn(pedido, statusEmAndamento);
    }

    /**
     * Retorna estatísticas de solicitações para o dashboard do admin
     */
    public Map<String, Long> getEstatisticas() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("pendentes", solicitacaoRepository.countByStatus(StatusSolicitacao.PENDENTE));
        stats.put("aprovadas", solicitacaoRepository.countByStatus(StatusSolicitacao.APROVADA));
        stats.put("recebidas", solicitacaoRepository.countByStatus(StatusSolicitacao.RECEBIDA));
        stats.put("concluidas", solicitacaoRepository.countByStatus(StatusSolicitacao.CONCLUIDA));
        stats.put("recusadas", solicitacaoRepository.countByStatus(StatusSolicitacao.RECUSADA));
        stats.put("canceladas", solicitacaoRepository.countByStatus(StatusSolicitacao.CANCELADA));
        stats.put("total", solicitacaoRepository.count());
        return stats;
    }
}