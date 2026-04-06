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

/**
 * Serviço responsável por gerenciar todo o fluxo de pagamentos do e-commerce.
 * Inclui validações, processamento de múltiplas formas de pagamento (cartões,
 * cupons de troca), cálculo de troco, e gerenciamento do status do pedido.
 *
 * garantindo atomicidade e consistência dos dados
 */
@Service
@Transactional
public class PagamentoService {

    /**
     * Constante que define o valor mínimo que cada cartão deve pagar
     * quando não há cupom de troca sendo utilizado.
     * Esta regra evita parcelamentos ou pagamentos muito pequenos em múltiplos cartões.
     */
    private static final BigDecimal VALOR_MINIMO_CARTAO = new BigDecimal("10.00");

    // Repositórios injetados para operações de banco de dados
    private final PagamentoRepository pagamentoRepository;           // Operações CRUD de pagamento
    private final PedidoRepository pedidoRepository;                 // Buscar/atualizar pedidos
    private final CartaoCreditoRepository cartaoCreditoRepository;   // Validar cartões do cliente
    private final CupomRepository cupomRepository;                   // Gerenciar cupons usados
    private final PagamentoCartaoRepository pagamentoCartaoRepository; // Persistir pagamentos com cartão

    /**
     * Construtor para injeção de dependências.
     * Spring automaticamente injeta as implementações dos repositórios.
     *
     * @param pagamentoRepository Repositório de pagamentos
     * @param pedidoRepository Repositório de pedidos
     * @param cartaoCreditoRepository Repositório de cartões de crédito
     * @param cupomRepository Repositório de cupons
     * @param pagamentoCartaoRepository Repositório de pagamentos com cartão (relacionamento)
     */
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

    /**
     * DTO (Data Transfer Object) para encapsular o resultado da validação
     * de pré-pagamento, antes mesmo da criação do pedido.
     *
     * Esta classe é estática para poder ser instanciada sem dependência da instância externa,
     * e pública pois é retornada para outras camadas da aplicação (controllers).
     */
    public static class ValidacaoPrePagamento {
        public final BigDecimal totalCarrinho;        // Valor total dos itens no carrinho
        public final BigDecimal totalCuponsTroca;     // Valor total dos cupons de troca aplicados
        public final BigDecimal totalCartoes;         // Valor total que será pago com cartões
        public final boolean possuiCupomCarrinho;     // Flag indicando se há cupom promocional
        public final BigDecimal totalAPagar;          // Valor final após descontos

        /**
         * Construtor que calcula automaticamente o total a pagar.
         *
         * @param totalCarrinho Valor bruto do carrinho
         * @param totalCuponsTroca Valor total dos cupons de troca
         * @param totalCartoes Valor total a ser pago com cartões
         * @param possuiCupomCarrinho Indica se há cupom promocional
         */
        public ValidacaoPrePagamento(BigDecimal totalCarrinho, BigDecimal totalCuponsTroca,
                                     BigDecimal totalCartoes, boolean possuiCupomCarrinho) {
            this.totalCarrinho = totalCarrinho;
            this.totalCuponsTroca = totalCuponsTroca;
            this.totalCartoes = totalCartoes;
            this.possuiCupomCarrinho = possuiCupomCarrinho;
            // Calcula o total a pagar: carrinho - cupons, nunca menor que zero
            this.totalAPagar = totalCarrinho.subtract(totalCuponsTroca).max(BigDecimal.ZERO);
        }
    }

    /**
     * DTO para encapsular o resultado da validação de pagamento
     * quando o pedido já existe no sistema.
     */
    public static class ValidacaoPedido {
        public final BigDecimal totalPedido;           // Valor total original do pedido
        public final BigDecimal totalDescontoCupons;   // Valor total descontado pelos cupons
        public final BigDecimal totalAPagar;           // Valor a pagar após descontos
        public final BigDecimal totalCartoes;          // Valor que será pago com cartões

        public ValidacaoPedido(BigDecimal totalPedido, BigDecimal totalDescontoCupons,
                               BigDecimal totalAPagar, BigDecimal totalCartoes) {
            this.totalPedido = totalPedido;
            this.totalDescontoCupons = totalDescontoCupons;
            this.totalAPagar = totalAPagar;
            this.totalCartoes = totalCartoes;
        }
    }

    // ===== VALIDAÇÕES =====

    /**
     * Valida as formas de pagamento antes da criação do pedido.
     * Útil para exibir erros ao cliente antes de finalizar a compra.
     *
     * @param clienteId ID do cliente que está realizando o pagamento
     * @param cuponsIds Lista de IDs dos cupons de troca que serão utilizados
     * @param cartoesValores Mapa onde a chave é o ID do cartão e o valor é o montante a ser pago
     * @param valorFrete Valor do frete do pedido
     * @return Objeto ValidacaoPrePagamento com os totais calculados
     * @throws RuntimeException Se alguma validação falhar (cupom inválido, valor insuficiente, etc)
     */
    public ValidacaoPrePagamento validarPrePagamento(Long clienteId,
                                                     List<Long> cuponsIds,
                                                     Map<Long, BigDecimal> cartoesValores,
                                                     BigDecimal valorFrete) {
        // NOTA: O totalCarrinho é definido como ZERO pois será calculado no checkout
        // Este método recebe valores já calculados pela camada de carrinho
        BigDecimal totalCarrinho = BigDecimal.ZERO;

        boolean possuiCupomTrocaSelecionado = false;
        BigDecimal totalCuponsTroca = BigDecimal.ZERO;

        // ===== VALIDAÇÃO DOS CUPONS DE TROCA =====
        if (cuponsIds != null && !cuponsIds.isEmpty()) {
            for (Long cupomId : cuponsIds) {
                // Busca o cupom pelo ID
                Cupom cupom = cupomRepository.findById(cupomId)
                        .orElseThrow(() -> new RuntimeException("Cupom não encontrado: " + cupomId));

                // Verifica se o cupom está válido (ativo, não expirado, usos disponíveis)
                if (!cupom.isValido()) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " inválido ou expirado");
                }

                // Verifica se o cupom pertence ao cliente (segurança)
                if (cupom.getCliente() == null || !cupom.getCliente().getId().equals(clienteId)) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " não pertence ao cliente");
                }

                // Garante que apenas cupons do tipo TROCA sejam usados aqui
                // Cupons promocionais são aplicados no carrinho, não no pagamento
                if (cupom.getTipo() != TipoCupom.TROCA) {
                    throw new RuntimeException("Apenas cupons de TROCA podem ser usados no pagamento");
                }

                // Acumula o valor total dos cupons
                totalCuponsTroca = totalCuponsTroca.add(cupom.getValor());
                possuiCupomTrocaSelecionado = true;
            }
        }

        // ===== VALIDAÇÃO DOS CARTÕES =====
        BigDecimal totalCartoes = BigDecimal.ZERO;
        if (cartoesValores != null) {
            for (Map.Entry<Long, BigDecimal> entry : cartoesValores.entrySet()) {
                // Garante que valor não seja nulo
                BigDecimal valor = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();

                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    // REGRA DE NEGÓCIO: Valor mínimo por cartão
                    // Se NÃO houver cupom de troca, cada cartão deve pagar pelo menos R$ 10,00
                    // Isso evita pagamentos muito pequenos pulverizados em vários cartões
                    if (!possuiCupomTrocaSelecionado && valor.compareTo(VALOR_MINIMO_CARTAO) < 0) {
                        throw new RuntimeException("Cada cartão deve pagar pelo menos R$ 10,00");
                    }

                    // Verifica se o cartão existe no sistema
                    CartaoCredito cartao = cartaoCreditoRepository.findById(entry.getKey())
                            .orElseThrow(() -> new RuntimeException("Cartão não encontrado: " + entry.getKey()));

                    // Verifica se o cartão pertence ao cliente (segurança)
                    if (!cartao.getCliente().getId().equals(clienteId)) {
                        throw new RuntimeException("Cartão não pertence ao cliente");
                    }
                }
                // Acumula o valor total que será pago com cartões
                totalCartoes = totalCartoes.add(valor);
            }
        }

        // Retorna o DTO com todos os valores calculados
        return new ValidacaoPrePagamento(totalCarrinho, totalCuponsTroca, totalCartoes, false);
    }

    /**
     * Valida o pagamento para um pedido que já existe no sistema.
     * Este método é usado quando o pedido já foi criado e o cliente
     * está finalizando o pagamento.
     *
     * @param pedidoId ID do pedido existente
     * @param cuponsIds Lista de IDs dos cupons de troca
     * @param cartoesValores Mapa de cartões e seus respectivos valores
     * @return Objeto ValidacaoPedido com os totais calculados
     * @throws RuntimeException Se o pedido não existir, já estiver pago, ou validações falharem
     */
    public ValidacaoPedido validarPagamentoPedido(Long pedidoId,
                                                  List<Long> cuponsIds,
                                                  Map<Long, BigDecimal> cartoesValores) {
        // Busca o pedido no banco de dados
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        // Verifica se o pedido já está em um estado que não permite novo pagamento
        // Pedidos pagos, enviados ou entregues não podem ter pagamento alterado
        if (pedido.getStatus() == StatusPedido.PAGO ||
                pedido.getStatus() == StatusPedido.ENVIADO ||
                pedido.getStatus() == StatusPedido.ENTREGUE) {
            throw new RuntimeException("Pedido não permite novo pagamento");
        }

        Long clienteId = pedido.getCliente().getId();

        // ===== VALIDAÇÃO DOS CUPONS DE TROCA =====
        // (mesma lógica do método anterior)
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

        // Calcula o valor do pedido após aplicar os descontos dos cupons
        // Garante que nunca fique negativo (caso cupons superem o valor do pedido)
        BigDecimal valorComDesconto = pedido.getValorTotal().subtract(totalDescontoCupons);
        if (valorComDesconto.compareTo(BigDecimal.ZERO) < 0) {
            valorComDesconto = BigDecimal.ZERO;
        }

        // ===== VALIDAÇÃO DOS CARTÕES =====
        BigDecimal totalCartoes = BigDecimal.ZERO;
        if (cartoesValores != null) {
            for (Map.Entry<Long, BigDecimal> entry : cartoesValores.entrySet()) {
                BigDecimal valor = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    // Mesma regra do valor mínimo por cartão
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

        // Verifica se o valor total dos cartões cobre o valor a pagar
        // Se for menor, o pagamento não pode ser processado
        if (totalCartoes.compareTo(valorComDesconto) < 0) {
            throw new RuntimeException("Valor dos cartões insuficiente para cobrir o pedido");
        }

        return new ValidacaoPedido(pedido.getValorTotal(), totalDescontoCupons, valorComDesconto, totalCartoes);
    }

    // ===== PROCESSAMENTO =====

    /**
     * Processa o pagamento de um pedido, registrando todas as informações
     * no sistema e reservando os recursos (cupons e cartões).
     *
     * Este método cria ou atualiza um registro de Pagamento, associa os cupons
     * e cartões utilizados, e atualiza o status do pedido para EM_ABERTO.
     *
     * @param pedidoId ID do pedido a ser pago
     * @param cuponsIds Lista de IDs dos cupons de troca
     * @param cartoesValores Mapa com ID do cartão e valor a ser pago
     * @param cartoesParcelas Mapa com ID do cartão e número de parcelas
     * @return Pagamento registrado no sistema
     * @throws RuntimeException Se pedido não existir ou validações falharem
     */
    @Transactional
    public Pagamento processarPagamento(Long pedidoId,
                                        List<Long> cuponsIds,
                                        Map<Long, BigDecimal> cartoesValores,
                                        Map<Long, Integer> cartoesParcelas) {

        // Busca o pedido no banco de dados
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        // Verifica se já existe um pagamento para este pedido
        Optional<Pagamento> pagamentoExistente = pagamentoRepository.findByPedidoId(pedidoId);
        Pagamento pagamento;

        if (pagamentoExistente.isPresent()) {
            // Se existe, reutiliza o pagamento existente
            pagamento = pagamentoExistente.get();

            // Limpa dados antigos para substituir pelas novas informações
            if (pagamento.getCuponsUsados() != null) {
                pagamento.getCuponsUsados().clear();  // Remove associação com cupons antigos
            }
            if (pagamento.getCartoesUsados() != null) {
                // Remove os registros de PagamentoCartao do banco de dados
                pagamentoCartaoRepository.deleteAll(pagamento.getCartoesUsados());
                pagamento.getCartoesUsados().clear();  // Limpa a lista
            }
        } else {
            // Se não existe, cria um novo pagamento
            pagamento = new Pagamento();
            pagamento.setPedido(pedido);  // Associa o pagamento ao pedido
        }

        // Define os dados básicos do pagamento
        pagamento.setFormaPagamento(FormaPagamento.CARTAO_CREDITO);
        pagamento.setStatus(StatusPagamento.PENDENTE);  // Aguardando confirmação
        pagamento.setDataPagamento(LocalDateTime.now());

        // ===== PROCESSAMENTO DOS CUPONS =====
        BigDecimal totalDesconto = BigDecimal.ZERO;
        List<Cupom> cuponsReservados = new ArrayList<>();

        if (cuponsIds != null && !cuponsIds.isEmpty()) {
            for (Long cupomId : cuponsIds) {
                Cupom cupom = cupomRepository.findById(cupomId)
                        .orElseThrow(() -> new RuntimeException("Cupom não encontrado: " + cupomId));

                // Verifica novamente a validade (segurança extra)
                if (!cupom.isValido()) {
                    throw new RuntimeException("Cupom " + cupom.getCodigo() + " inválido");
                }

                // RESERVA O CUPOM: desativa temporariamente para evitar uso duplicado
                // Enquanto o pagamento não é confirmado, o cupom fica indisponível
                cupom.setAtivo(false);
                cupomRepository.save(cupom);

                cuponsReservados.add(cupom);
                totalDesconto = totalDesconto.add(cupom.getValor());
            }

            // Associa os cupons ao pagamento
            pagamento.setCuponsUsados(cuponsReservados);

            // Cria um resumo textual dos cupons para fácil visualização
            String resumo = cuponsReservados.stream()
                    .map(Cupom::getCodigo)
                    .collect(Collectors.joining(", "));
            pagamento.setResumoCupons(resumo);
        }

        pagamento.setDesconto(totalDesconto);

        // Calcula o valor que será pago com cartões após o desconto dos cupons
        BigDecimal valorComDesconto = pedido.getValorTotal().subtract(totalDesconto);
        if (valorComDesconto.compareTo(BigDecimal.ZERO) < 0) {
            valorComDesconto = BigDecimal.ZERO;
        }
        pagamento.setValor(valorComDesconto);

        // ===== PROCESSAMENTO DOS CARTÕES =====
        BigDecimal totalCartoes = BigDecimal.ZERO;
        if (cartoesValores != null) {
            for (Map.Entry<Long, BigDecimal> entry : cartoesValores.entrySet()) {
                BigDecimal valor = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                if (valor.compareTo(BigDecimal.ZERO) > 0) {
                    // Busca o cartão de crédito
                    CartaoCredito cartao = cartaoCreditoRepository.findById(entry.getKey())
                            .orElseThrow(() -> new RuntimeException("Cartão não encontrado: " + entry.getKey()));

                    // Define o número de parcelas (padrão = 1 se não especificado)
                    int parcelas = 1;
                    if (cartoesParcelas != null && cartoesParcelas.containsKey(entry.getKey())) {
                        parcelas = cartoesParcelas.get(entry.getKey());
                    }

                    // CRIA O REGISTRO DE PAGAMENTO COM CARTÃO
                    // Esta é a entidade de relacionamento entre Pagamento e CartaoCredito
                    PagamentoCartao pagamentoCartao = new PagamentoCartao();
                    pagamentoCartao.setPagamento(pagamento);           // Associa ao pagamento
                    pagamentoCartao.setCartaoCredito(cartao);          // Associa ao cartão
                    pagamentoCartao.setValorPago(valor);               // Valor pago com este cartão
                    pagamentoCartao.setParcelas(parcelas);             // Número de parcelas

                    // Adiciona à lista - com CascadeType.ALL, será salvo automaticamente
                    pagamento.getCartoesUsados().add(pagamentoCartao);

                    totalCartoes = totalCartoes.add(valor);
                }
            }
        }

        // Verificação final: o valor dos cartões deve cobrir o valor a pagar
        if (totalCartoes.compareTo(valorComDesconto) < 0) {
            throw new RuntimeException("Valor dos cartões insuficiente para cobrir o pedido");
        }

        // Persiste o pagamento no banco de dados
        // O CascadeType.ALL vai salvar os PagamentoCartao automaticamente
        pagamento = pagamentoRepository.save(pagamento);

        // Logs para debug (podem ser substituídos por logger oficial)
        System.out.println("Pagamento salvo com ID: " + pagamento.getId());
        System.out.println("Cartões vinculados: " + pagamento.getCartoesUsados().size());

        // Atualiza o pedido com a referência do pagamento e muda o status
        pedido.setPagamento(pagamento);
        pedido.setStatus(StatusPedido.EM_ABERTO);  // Aguardando confirmação do pagamento
        pedidoRepository.save(pedido);

        return pagamento;
    }

    /**
     * Confirma o pagamento após a aprovação da transação financeira.
     * Este método é chamado quando o gateway de pagamento retorna sucesso.
     *
     * Ações realizadas:
     * 1. Aprova o pagamento (muda status para APROVADO)
     * 2. Confirma o pedido (muda status para PAGO)
     * 3. Gera cupom de troco se houver valor excedente
     * 4. Remove/desativa os cupons utilizados
     *
     * @param pagamentoId ID do pagamento a ser confirmado
     * @return Pagamento atualizado com status APROVADO
     * @throws RuntimeException Se pagamento não for encontrado
     */
    @Transactional
    public Pagamento confirmarPagamento(Long pagamentoId) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + pagamentoId));

        // Chama o método da entidade que aprova o pagamento
        // Internamente muda o status para APROVADO e registra a data
        pagamento.aprovar();

        // Atualiza o pedido: muda status para PAGO
        Pedido pedido = pagamento.getPedido();
        pedido.confirmarPagamento();
        pedidoRepository.save(pedido);

        // ===== CÁLCULO DE TROCO =====
        // Se o valor dos cupons de troca excedeu o valor do pedido,
        // o excedente deve ser devolvido como novo cupom de troca
        BigDecimal valorPedido = pedido.getValorTotal();
        BigDecimal totalDesconto = pagamento.getDesconto();
        BigDecimal troco = totalDesconto.subtract(valorPedido);

        if (troco.compareTo(BigDecimal.ZERO) > 0) {
            try {
                Long clienteId = pedido.getCliente().getId();
                String descricao = "Troco do pagamento do pedido #" + pedido.getId();

                // Cria um novo cupom de troca com o valor excedente
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
                // Não interrompe o fluxo principal se falhar a criação do cupom de troco
                // O pagamento já foi confirmado, isso é apenas um bônus
                // Em produção, seria bom logar este erro
            }
        }

        // ===== CONSUMO DOS CUPONS USADOS =====
        // Remove os cupons do sistema (ou os desativa permanentemente)
        List<Cupom> cuponsUsados = new ArrayList<>(pagamento.getCuponsUsados());
        pagamento.getCuponsUsados().clear();  // Remove o vínculo com o pagamento
        pagamentoRepository.save(pagamento);  // Persiste a remoção do vínculo

        for (Cupom cupom : cuponsUsados) {
            try {
                // Tenta deletar permanentemente o cupom
                cupomRepository.delete(cupom);
            } catch (Exception ex) {
                // Se não puder deletar (ex: constraints do banco), apenas desativa
                cupom.setAtivo(false);
                cupomRepository.save(cupom);
            }
        }

        // Retorna o pagamento atualizado
        return pagamentoRepository.save(pagamento);
    }

    /**
     * Rejeita o pagamento (geralmente por erro na transação ou recusa do banco).
     *
     * Ações realizadas:
     * 1. Rejeita o pagamento com motivo
     * 2. Reativa os cupons que haviam sido reservados
     * 3. Cancela o pedido
     *
     * @param pagamentoId ID do pagamento rejeitado
     * @param motivo Motivo da rejeição (ex: "Saldo insuficiente", "Cartão bloqueado")
     * @return Pagamento atualizado com status REJEITADO
     */
    @Transactional
    public Pagamento rejeitarPagamento(Long pagamentoId, String motivo) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado: " + pagamentoId));

        // Chama o método da entidade que rejeita o pagamento
        pagamento.rejeitar(motivo);

        // ===== REATIVA OS CUPONS RESERVADOS =====
        // Como o pagamento falhou, os cupons devem voltar a ficar disponíveis
        if (pagamento.getCuponsUsados() != null) {
            for (Cupom cupom : pagamento.getCuponsUsados()) {
                cupom.setAtivo(true);  // Reativa o cupom
                cupomRepository.save(cupom);
            }
            pagamento.getCuponsUsados().clear();  // Remove associação
        }

        // Cancela o pedido (status muda para CANCELADO)
        Pedido pedido = pagamento.getPedido();
        pedido.cancelar();
        pedidoRepository.save(pedido);

        return pagamentoRepository.save(pagamento);
    }

    // ===== CONSULTAS =====

    /**
     * Busca um pagamento pelo seu ID.
     *
     * @param id ID do pagamento
     * @return Optional contendo o pagamento se encontrado
     */
    public Optional<Pagamento> buscarPorId(Long id) {
        return pagamentoRepository.findById(id);
    }

    /**
     * Busca o pagamento associado a um pedido específico.
     *
     * @param pedidoId ID do pedido
     * @return Optional com o pagamento se existir
     */
    public Optional<Pagamento> buscarPorPedidoId(Long pedidoId) {
        return pagamentoRepository.findByPedidoId(pedidoId);
    }

    /**
     * Lista todos os pagamentos do sistema.
     *
     * @return Lista completa de pagamentos
     */
    public List<Pagamento> listarTodos() {
        return pagamentoRepository.findAll();
    }

    /**
     * Lista todos os pagamentos realizados por um cliente específico.
     * Útil para o histórico de compras do cliente.
     *
     * @param clienteId ID do cliente
     * @return Lista de pagamentos do cliente
     */
    public List<Pagamento> listarPorCliente(Long clienteId) {
        return pagamentoRepository.findByClienteId(clienteId);
    }

    /**
     * Lista pagamentos filtrados por status.
     * Útil para administração (ex: ver todos pagamentos pendentes).
     *
     * @param status Status desejado (PENDENTE, APROVADO, REJEITADO)
     * @return Lista de pagamentos com o status especificado
     */
    public List<Pagamento> listarPorStatus(StatusPagamento status) {
        return pagamentoRepository.findByStatus(status);
    }

    /**
     * Salva/atualiza um pagamento diretamente.
     * Uso geralmente restrito a operações administrativas.
     *
     * @param pagamento Entidade Pagamento a ser salva
     * @return Pagamento persistido
     */
    public Pagamento salvar(Pagamento pagamento) {
        return pagamentoRepository.save(pagamento);
    }

    /**
     * Verifica se existe algum pagamento associado a um pedido.
     *
     * @param pedidoId ID do pedido
     * @return true se existir pagamento, false caso contrário
     */
    public boolean existePagamentoParaPedido(Long pedidoId) {
        return pagamentoRepository.existsByPedidoId(pedidoId);
    }
}