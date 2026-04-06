package br.com.java.e_commerce.nexus.service.venda;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import br.com.java.e_commerce.nexus.model.carrinho.ItemCarrinho;
import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.venda.ItemPedido;
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

/**
 * Serviço responsável por gerenciar todas as operações relacionadas a pedidos
 * no sistema de e-commerce.
 *
 * Principais responsabilidades:
 * - Criar pedidos a partir do carrinho de compras
 * - Gerenciar o ciclo de vida do pedido (pagamento, envio, entrega, cancelamento)
 * - Processar devoluções e gerar cupons de troca
 * - Fornecer consultas e relatórios de vendas
 *
 */
@Service
@Transactional
public class PedidoService {

    // Repositórios injetados para operações de persistência
    private final PedidoRepository pedidoRepository;       // Operações CRUD de pedidos
    private final CarrinhoService carrinhoService;        // Serviço para gerenciar o carrinho
    private final ClienteRepository clienteRepository;     // Buscar informações do cliente
    private final EnderecoRepository enderecoRepository;   // Validar endereço de entrega
    private final PagamentoRepository pagamentoRepository; // Persistir pagamentos
    private final CupomRepository cupomRepository;         // Gerenciar cupons usados

    /**
     * Construtor para injeção de dependências.
     * Spring automaticamente injeta todas as dependências necessárias.
     *
     * @param pedidoRepository Repositório de pedidos
     * @param carrinhoService Serviço do carrinho (para acessar itens e limpar após pedido)
     * @param clienteRepository Repositório de clientes
     * @param enderecoRepository Repositório de endereços
     * @param pagamentoRepository Repositório de pagamentos
     * @param cupomRepository Repositório de cupons
     */
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

    /**
     * Cria um pedido a partir do carrinho de compras do cliente.
     *
     * Este é um dos métodos mais importantes do sistema. Ele:
     * 1. Obtém o carrinho do cliente
     * 2. Valida se o carrinho não está vazio
     * 3. Verifica se o endereço pertence ao cliente
     * 4. Valida o estoque de todos os produtos
     * 5. Cria o pedido com os valores congelados (preço no momento da compra)
     * 6. Dá baixa no estoque
     * 7. Processa os cupons utilizados
     * 8. Limpa o carrinho
     *
     * @param clienteId ID do cliente que está fazendo o pedido
     * @param enderecoEntregaId ID do endereço de entrega selecionado
     * @return Pedido criado e persistido no banco de dados
     * @throws RuntimeException Se carrinho vazio, cliente/endereço não encontrado,
     *         endereço não pertence ao cliente, ou validações falharem
     * @throws EstoqueInsuficienteException Se algum produto não tem estoque suficiente
     */
    @Transactional
    public Pedido criarPedidoDoCarrinho(Long clienteId, Long enderecoEntregaId) {
        // ===== 1. OBTÉM O CARRINHO =====
        Carrinho carrinho = carrinhoService.obterCarrinhoDoCliente(clienteId);

        // Verifica se o carrinho tem itens
        if (carrinho.isEmpty()) {
            throw new RuntimeException("Carrinho está vazio");
        }

        // ===== 2. VALIDA CLIENTE E ENDEREÇO =====
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        Endereco enderecoEntrega = enderecoRepository.findById(enderecoEntregaId)
                .orElseThrow(() -> new RuntimeException("Endereço de entrega não encontrado"));

        // Segurança: verifica se o endereço realmente pertence ao cliente
        // Evita que um cliente use endereço de outro cliente
        if (!enderecoEntrega.getCliente().getId().equals(clienteId)) {
            throw new RuntimeException("Endereço não pertence ao cliente");
        }

        // ===== 3. VALIDA ESTOQUE =====
        // Percorre todos os itens do carrinho verificando disponibilidade
        for (ItemCarrinho item : carrinho.getItens()) {
            if (!item.getProduto().temEstoque(item.getQuantidade())) {
                throw new EstoqueInsuficienteException(
                        "Produto " + item.getProduto().getTitulo() + " sem estoque suficiente"
                );
            }
        }

        // ===== 4. CRIA O PEDIDO =====
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setEnderecoEntrega(enderecoEntrega);

        // ===== 5. CRIA OS ITENS DO PEDIDO =====
        // É fundamental congelar o preço atual do produto no momento da compra
        // Isso protege o cliente caso o preço mude depois
        List<ItemPedido> itensPedido = new ArrayList<>();
        for (ItemCarrinho item : carrinho.getItens()) {
            ItemPedido itemPedido = new ItemPedido();
            itemPedido.setProduto(item.getProduto());
            itemPedido.setQuantidade(item.getQuantidade());
            itemPedido.setPrecoUnitario(item.getProduto().getPreco()); // CONGELA O PREÇO ATUAL
            itemPedido.setPedido(pedido); // Estabelece relação bidirecional (importante para JPA)
            itensPedido.add(itemPedido);
        }

        pedido.setItens(itensPedido);

        // Copia os valores de frete e descontos do carrinho
        pedido.setValorFrete(carrinho.getValorFrete());
        pedido.setDescontoPromocional(carrinho.getDescontoTotal());

        // Cria um resumo dos cupons promocionais aplicados (para histórico)
        pedido.setResumoCuponsPromocionais(
                carrinho.getCupons().stream()
                        .filter(c -> c.getTipo() == TipoCupom.PROMOCIONAL)
                        .map(Cupom::getCodigo)
                        .collect(Collectors.joining(", "))
        );

        // Define status inicial e data de criação
        pedido.setStatus(StatusPedido.EM_ABERTO);
        pedido.setDataCriacao(LocalDateTime.now());

        // Persiste o pedido no banco de dados
        // O CascadeType.ALL vai salvar os ItensPedido automaticamente
        pedido = pedidoRepository.save(pedido);

        // ===== 6. DÁ BAIXA NO ESTOQUE =====
        // Após criar o pedido, reservamos o estoque para garantir a disponibilidade
        for (ItemPedido item : itensPedido) {
            item.getProduto().baixarEstoque(item.getQuantidade());
        }

        // ===== 7. PROCESSA OS CUPONS =====
        // Consome os cupons utilizados no carrinho (marca como usados)
        processarCuponsAposPedido(carrinho, pedido);

        // ===== 8. LIMPA O CARRINHO =====
        // O pedido foi criado, então o carrinho pode ser esvaziado
        carrinhoService.limparCarrinho(clienteId);

        return pedido;
    }

    /**
     * Processa os cupons utilizados no carrinho após a criação do pedido.
     * Este método consome os cupons, ou seja, marca que eles foram usados
     * e atualiza seu contador de usos.
     *
     * @param carrinho Carrinho com os cupons aplicados
     * @param pedido Pedido recém-criado (usado apenas para contexto, não diretamente)
     */
    private void processarCuponsAposPedido(Carrinho carrinho, Pedido pedido) {
        // Cria uma cópia da lista para evitar problemas de concorrência
        List<Cupom> cuponsUsados = new ArrayList<>(carrinho.getCupons());

        // Para cada cupom, consome uma unidade de uso
        for (Cupom cupom : cuponsUsados) {
            cupom.consumir();  // Incrementa o contador e desativa se atingir o limite
            cupomRepository.save(cupom);
        }
    }

    // ===== PAGAMENTO =====

    /**
     * Processa o pagamento de um pedido.
     *
     * NOTA: Este método parece ser uma versão simplificada do fluxo de pagamento.
     * Em um sistema real, o pagamento seria processado pelo PagamentoService,
     * que lida com múltiplos cartões, cupons de troca, etc.
     *
     * @param pedidoId ID do pedido a ser pago
     * @param pagamento Objeto Pagamento com os dados da transação
     * @return Pedido atualizado com status PAGO
     * @throws RuntimeException Se pedido não encontrado ou não estiver em aberto
     */
    @Transactional
    public Pedido processarPagamento(Long pedidoId, Pagamento pagamento) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        // Verifica se o pedido está em um estado que permite pagamento
        if (pedido.getStatus() != StatusPedido.EM_ABERTO) {
            throw new RuntimeException("Pedido não está em aberto para pagamento");
        }

        // Associa o pagamento ao pedido e preenche os dados
        pagamento.setPedido(pedido);
        pagamento.setValor(pedido.getValorTotal());
        pagamento.setDataPagamento(LocalDateTime.now());
        pagamento.setStatus(StatusPagamento.APROVADO); // Aprova automaticamente (simplificado)

        // Persiste o pagamento
        pagamentoRepository.save(pagamento);

        // Atualiza o pedido com a referência do pagamento e confirma
        pedido.setPagamento(pagamento);
        pedido.confirmarPagamento(); // Muda status para PAGO

        return pedidoRepository.save(pedido);
    }

    // ===== ATUALIZAÇÃO DE STATUS =====

    /**
     * Atualiza o status de um pedido seguindo o fluxo normal do ciclo de vida.
     *
     * Fluxo de status esperado:
     * EM_ABERTO → PAGO → ENVIADO → ENTREGUE
     *
     * Regras de validação:
     * - Não pode alterar status de pedido cancelado
     * - Só pode enviar se estiver pago
     * - Só pode entregar se estiver enviado
     *
     * @param id ID do pedido
     * @param novoStatus Novo status a ser aplicado
     * @return Pedido atualizado
     * @throws RuntimeException Se pedido não encontrado ou regras de transição violadas
     */
    @Transactional
    public Pedido atualizarStatus(Long id, StatusPedido novoStatus) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        StatusPedido statusAtual = pedido.getStatus();

        // Pedidos cancelados são terminais - não podem mais mudar de status
        if (statusAtual == StatusPedido.CANCELADO) {
            throw new RuntimeException("Pedido cancelado não pode ter status alterado");
        }

        // ===== VALIDAÇÕES DE TRANSIÇÃO DE STATUS =====

        // Regra: Só pode enviar um pedido que já foi pago
        if (novoStatus == StatusPedido.ENVIADO && statusAtual != StatusPedido.PAGO) {
            throw new RuntimeException("Pedido precisa estar pago para ser enviado");
        }

        // Regra: Só pode entregar um pedido que já foi enviado
        if (novoStatus == StatusPedido.ENTREGUE && statusAtual != StatusPedido.ENVIADO) {
            throw new RuntimeException("Pedido precisa estar enviado para ser entregue");
        }

        // Aplica o novo status
        pedido.setStatus(novoStatus);

        // ===== REGISTRA DATAS ESPECÍFICAS CONFORME O STATUS =====
        switch (novoStatus) {
            case PAGO:
                // Registra quando o pagamento foi confirmado
                pedido.setDataConfirmacao(LocalDateTime.now());
                break;
            case ENVIADO:
                // Registra data de envio e gera código de rastreio
                pedido.setDataEnvio(LocalDateTime.now());
                pedido.setCodigoRastreio(gerarCodigoRastreio());
                break;
            case ENTREGUE:
                // Registra data de entrega
                pedido.setDataEntrega(LocalDateTime.now());
                break;
            // Outros status (EM_ABERTO, CANCELADO, etc) não têm datas específicas
        }

        return pedidoRepository.save(pedido);
    }

    /**
     * Gera um código de rastreio único para o pedido.
     * Formato: BR + 8 caracteres alfanuméricos maiúsculos
     * Exemplo: "BR3F7D2A9B"
     *
     * @return Código de rastreio gerado aleatoriamente
     */
    private String gerarCodigoRastreio() {
        return "BR" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Cancela um pedido, desde que ele ainda não tenha sido enviado ou entregue.
     *
     * Regras de cancelamento:
     * - Pedido deve ter itens (válido)
     * - Não pode estar entregue
     * - Não pode estar enviado (já saiu para entrega)
     *
     * Se o pedido já foi pago (e portanto o estoque foi baixado),
     * o estoque é estornado automaticamente.
     *
     * @param id ID do pedido a ser cancelado
     * @return Pedido com status alterado para CANCELADO
     * @throws RuntimeException Se regras de cancelamento forem violadas
     */
    @Transactional
    public Pedido cancelarPedido(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        // Verifica se o pedido é válido (tem itens)
        if (!pedido.temItens()) {
            throw new RuntimeException("Pedido inválido (sem itens) não pode ser cancelado");
        }

        // Verifica se já foi entregue (não pode cancelar)
        if (pedido.getStatus() == StatusPedido.ENTREGUE) {
            throw new RuntimeException("Pedido já entregue não pode ser cancelado");
        }

        // Verifica se já foi enviado (não pode cancelar)
        if (pedido.getStatus() == StatusPedido.ENVIADO) {
            throw new RuntimeException("Pedido já enviado não pode ser cancelado");
        }

        // Cancela o pedido (muda status para CANCELADO)
        pedido.cancelar();

        // ===== ESTORNA ESTOQUE SE NECESSÁRIO =====
        // Se o pedido já estava pago, o estoque foi baixado na criação
        // Precisamos devolver os produtos ao estoque
        if (pedido.getStatus() == StatusPedido.PAGO) {
            for (ItemPedido item : pedido.getItens()) {
                item.getProduto().reporEstoque(item.getQuantidade());
            }
        }

        return pedidoRepository.save(pedido);
    }

    // ===== DEVOLUÇÃO =====

    /**
     * Confirma a devolução de um pedido e gera um cupom de troca.
     *
     * Este método é chamado quando o cliente devolveu os produtos
     * e a loja já recebeu e confirmou a devolução.
     *
     * Fluxo:
     * 1. Verifica se o pedido está aguardando devolução
     * 2. Muda status para DEVOLUCAO_CONFIRMADA
     * 3. Estorna o estoque (produtos voltam a ficar disponíveis)
     * 4. Cria um cupom de troca com o valor total do pedido
     *
     * @param pedidoId ID do pedido devolvido
     * @return Cupom de troca gerado para o cliente
     * @throws RuntimeException Se pedido não encontrado, sem itens, ou não aguardando devolução
     */
    @Transactional
    public Cupom confirmarDevolucaoEGerarCupom(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));

        // Verifica se o pedido é válido
        if (!pedido.temItens()) {
            throw new RuntimeException("Pedido inválido (sem itens) não pode ser devolvido");
        }

        // Verifica se o pedido está no estado correto para devolução
        // Este status provavelmente é definido por um processo anterior (solicitação de devolução)
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_DEVOLUCAO) {
            throw new RuntimeException("O pedido não está aguardando devolução");
        }

        // Confirma a devolução
        pedido.setStatus(StatusPedido.DEVOLUCAO_CONFIRMADA);
        pedidoRepository.save(pedido);

        // ===== ESTORNA ESTOQUE =====
        // Os produtos devolvidos voltam para o estoque
        for (ItemPedido item : pedido.getItens()) {
            item.getProduto().reporEstoque(item.getQuantidade());
        }

        // ===== CRIA CUPOM DE TROCA =====
        // O cliente recebe um crédito no valor total do pedido
        Cupom cupom = new Cupom();
        cupom.setCodigo("TROCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        cupom.setTipo(TipoCupom.TROCA);
        cupom.setValor(pedido.getValorTotal());
        cupom.setCliente(pedido.getCliente());
        cupom.setDescricao("Cupom de troca referente ao pedido #" + pedido.getId());
        cupom.setDataValidade(LocalDateTime.now().plusMonths(6)); // Válido por 6 meses
        cupom.setAtivo(true);
        cupom.setMaximoUsos(1); // Uso único

        return cupomRepository.save(cupom);
    }

    // ===== CONSULTAS =====

    /**
     * Busca um pedido pelo ID (sem carregar relacionamentos adicionais).
     *
     * @param id ID do pedido
     * @return Optional contendo o pedido se encontrado
     */
    @Transactional(readOnly = true)
    public Optional<Pedido> buscarPorId(Long id) {
        return pedidoRepository.findById(id);
    }

    /**
     * Busca um pedido pelo ID com todos os seus detalhes.
     * Como o método é transacional (readOnly=true), os relacionamentos LAZY
     * podem ser acessados dentro deste método sem erro de LazyInitializationException.
     *
     * @param id ID do pedido
     * @return Pedido completo com itens, pagamento, etc.
     * @throws RuntimeException Se pedido não for encontrado
     */
    @Transactional(readOnly = true)
    public Pedido buscarComDetalhes(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado"));
        // Os relacionamentos LAZY podem ser acessados dentro desta transação
    }

    /**
     * Lista todos os pedidos do sistema.
     *
     * @return Lista completa de pedidos
     */
    @Transactional(readOnly = true)
    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    /**
     * Lista todos os pedidos de um cliente específico.
     * Utiliza uma query customizada que carrega os itens junto (evita N+1 queries).
     *
     * @param clienteId ID do cliente
     * @return Lista de pedidos do cliente
     */
    @Transactional(readOnly = true)
    public List<Pedido> listarPorCliente(Long clienteId) {
        return pedidoRepository.findByClienteIdWithItens(clienteId);
    }

    /**
     * Lista pedidos filtrados por status.
     *
     * @param status Status desejado (PAGO, ENVIADO, ENTREGUE, etc.)
     * @return Lista de pedidos com o status especificado
     */
    @Transactional(readOnly = true)
    public List<Pedido> listarPorStatus(StatusPedido status) {
        return pedidoRepository.findByStatus(status);
    }

    // ===== RELATÓRIOS =====

    /**
     * Calcula o total de vendas agrupado por categoria de produto.
     *
     * @param inicio Data/hora inicial do período (inclusiva)
     * @param fim Data/hora final do período (inclusiva)
     * @return Mapa onde a chave é o nome da categoria (gênero) e o valor é o total vendido
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> calcularVendasPorCategoria(LocalDateTime inicio, LocalDateTime fim) {
        // Busca todos os pedidos no período especificado
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);
        Map<String, BigDecimal> vendasPorCategoria = new HashMap<>();

        for (Pedido pedido : pedidos) {
            // Pula pedidos inválidos (sem itens)
            if (!pedido.temItens()) continue;

            // Para cada item do pedido, acumula o valor na categoria correspondente
            for (ItemPedido item : pedido.getItens()) {
                String categoria = item.getProduto().getGenero(); // Ex: "MASCULINO", "FEMININO"
                BigDecimal total = item.getPrecoTotal(); // Usa o preço congelado no momento da compra

                // Merge: se categoria já existe, soma; se não, cria com o valor
                vendasPorCategoria.merge(categoria, total, BigDecimal::add);
            }
        }
        return vendasPorCategoria;
    }

    /**
     * Calcula a quantidade vendida por produto.
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Mapa onde a chave é o título do produto e o valor é a quantidade total vendida
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> calcularVendasPorProduto(LocalDateTime inicio, LocalDateTime fim) {
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);
        Map<String, Integer> vendasPorProduto = new HashMap<>();

        for (Pedido pedido : pedidos) {
            if (!pedido.temItens()) continue;
            for (ItemPedido item : pedido.getItens()) {
                String titulo = item.getProduto().getTitulo();
                // Soma a quantidade vendida deste produto
                vendasPorProduto.merge(titulo, item.getQuantidade(), Integer::sum);
            }
        }
        return vendasPorProduto;
    }

    /**
     * Calcula o valor total de vendas no período especificado.
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Soma total dos valores de todos os pedidos no período
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularTotalVendas(LocalDateTime inicio, LocalDateTime fim) {
        return pedidoRepository.findVendasPorPeriodo(inicio, fim).stream()
                .filter(Pedido::temItens)  // Apenas pedidos válidos
                .map(Pedido::getValorTotal) // Extrai o valor total de cada pedido
                .reduce(BigDecimal.ZERO, BigDecimal::add); // Soma todos
    }

    /**
     * Conta o número total de vendas (pedidos) no período especificado.
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Quantidade de pedidos válidos no período
     */
    @Transactional(readOnly = true)
    public long contarVendas(LocalDateTime inicio, LocalDateTime fim) {
        return pedidoRepository.findVendasPorPeriodo(inicio, fim).stream()
                .filter(Pedido::temItens) // Apenas pedidos válidos
                .count();
    }
}