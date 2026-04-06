package br.com.java.e_commerce.nexus.service.carrinho;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import br.com.java.e_commerce.nexus.model.carrinho.ItemCarrinho;
import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.model.produto.Produto;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.repository.carrinho.CarrinhoRepository;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.cliente.EnderecoRepository;
import br.com.java.e_commerce.nexus.repository.produto.ProdutoRepository;
import br.com.java.e_commerce.nexus.repository.venda.CupomRepository;
import br.com.java.e_commerce.nexus.service.exception.EstoqueInsuficienteException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Principais responsabilidades:
 * - Gerenciar itens do carrinho (adicionar, remover, alterar quantidade)
 * - Aplicar e remover cupons (promocionais e de troca)
 * - Calcular frete baseado no endereço do cliente
 * - Fornecer informações consolidadas do carrinho (subtotal, total, quantidade)
 */
@Service
@Transactional
public class CarrinhoService {

    // Repositórios e serviços injetados
    private final CarrinhoRepository carrinhoRepository;  // Persistência do carrinho
    private final ClienteRepository clienteRepository;    // Buscar dados do cliente
    private final ProdutoRepository produtoRepository;    // Validar produtos e estoque
    private final CupomRepository cupomRepository;        // Validar cupons
    private final EnderecoRepository enderecoRepository;  // Buscar endereço para frete
    private final FreteService freteService;              // Serviço de cálculo de frete

    /**
     * Construtor para injeção de dependências.
     * Spring injeta automaticamente todas as dependências.
     *
     * @param carrinhoRepository Repositório de carrinhos
     * @param clienteRepository Repositório de clientes
     * @param produtoRepository Repositório de produtos
     * @param cupomRepository Repositório de cupons
     * @param enderecoRepository Repositório de endereços
     * @param freteService Serviço de cálculo de frete
     */
    public CarrinhoService(CarrinhoRepository carrinhoRepository,
                           ClienteRepository clienteRepository,
                           ProdutoRepository produtoRepository,
                           CupomRepository cupomRepository,
                           EnderecoRepository enderecoRepository,
                           FreteService freteService) {
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.cupomRepository = cupomRepository;
        this.enderecoRepository = enderecoRepository;
        this.freteService = freteService;
    }

    // ===== MÉTODOS PRINCIPAIS =====

    /**
     * Obtém o carrinho de um cliente. Se o cliente não tiver um carrinho,
     * cria um novo automaticamente.
     *
     * Este método é fundamental e é chamado por praticamente todos os outros
     * métodos do serviço. Ele implementa o padrão "Get or Create".
     *
     * @param clienteId ID do cliente
     * @return Carrinho do cliente (existente ou recém-criado)
     * @throws RuntimeException Se o cliente não for encontrado
     */
    public Carrinho obterCarrinhoDoCliente(Long clienteId) {
        // Busca o cliente no banco de dados
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado com ID: " + clienteId));

        // Tenta encontrar o carrinho do cliente
        return carrinhoRepository.findByClienteId(clienteId)
                .orElseGet(() -> {
                    // Se não existir, cria um novo carrinho vazio
                    Carrinho novoCarrinho = new Carrinho();
                    novoCarrinho.setCliente(cliente);
                    // Persiste o novo carrinho antes de retornar
                    return carrinhoRepository.save(novoCarrinho);
                });
    }

    /**
     * Adiciona um item ao carrinho do cliente.
     *
     * Se o produto já existir no carrinho, aumenta a quantidade.
     * Se não existir, adiciona um novo item.
     *
     * @param clienteId ID do cliente
     * @param produtoId ID do produto a ser adicionado
     * @param quantidade Quantidade a ser adicionada
     * @return Carrinho atualizado
     * @throws IllegalArgumentException Se quantidade <= 0
     * @throws RuntimeException Se produto não encontrado
     * @throws EstoqueInsuficienteException Se não houver estoque suficiente
     */
    public Carrinho adicionarItem(Long clienteId, Long produtoId, int quantidade) {
        // Validação básica de quantidade
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        }

        // Obtém ou cria o carrinho do cliente
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        // Busca o produto no banco de dados
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com ID: " + produtoId));

        // ===== VALIDAÇÃO DE ESTOQUE =====
        // Verifica se há quantidade suficiente disponível no estoque
        if (!produto.temEstoque(quantidade)) {
            throw new EstoqueInsuficienteException(
                    "Estoque insuficiente para o livro: " + produto.getTitulo() +
                            ". Disponível: " + produto.getEstoque()
            );
        }

        // Delega a lógica de adicionar/atualizar item para a entidade Carrinho
        // A entidade cuida de verificar se o item já existe e atualizar a quantidade
        carrinho.adicionarItem(produto, quantidade);

        // Persiste as alterações no banco de dados
        return carrinhoRepository.save(carrinho);
    }

    /**
     * Altera a quantidade de um item existente no carrinho.
     *
     * Se a nova quantidade for <= 0, remove o item completamente.
     * Se for aumentar a quantidade, verifica se há estoque disponível para o acréscimo.
     *
     * @param clienteId ID do cliente
     * @param produtoId ID do produto a ter a quantidade alterada
     * @param novaQuantidade Nova quantidade desejada
     * @return Carrinho atualizado
     * @throws RuntimeException Se o item não existir no carrinho
     * @throws EstoqueInsuficienteException Se não houver estoque para o aumento
     */
    public Carrinho alterarQuantidadeItem(Long clienteId, Long produtoId, int novaQuantidade) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        // Caso 1: Nova quantidade <= 0 → remove o item
        if (novaQuantidade <= 0) {
            carrinho.removerItem(produtoId);
        }
        // Caso 2: Nova quantidade > 0 → atualiza a quantidade
        else {
            // Primeiro, encontra o item no carrinho
            ItemCarrinho itemExistente = carrinho.getItens().stream()
                    .filter(item -> item.getProduto().getId().equals(produtoId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Item não encontrado no carrinho"));

            // Se for aumentar a quantidade, valida o estoque adicional necessário
            if (novaQuantidade > itemExistente.getQuantidade()) {
                Produto produto = itemExistente.getProduto();
                int diferenca = novaQuantidade - itemExistente.getQuantidade();

                if (!produto.temEstoque(diferenca)) {
                    throw new EstoqueInsuficienteException(
                            "Estoque insuficiente para aumentar quantidade. Disponível: " + produto.getEstoque()
                    );
                }
            }

            // Delega a alteração para a entidade Carrinho
            carrinho.alterarQuantidadeItem(produtoId, novaQuantidade);
        }

        return carrinhoRepository.save(carrinho);
    }

    /**
     * Remove um item completamente do carrinho.
     *
     * @param clienteId ID do cliente
     * @param produtoId ID do produto a ser removido
     * @return Carrinho atualizado
     */
    public Carrinho removerItem(Long clienteId, Long produtoId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);
        carrinho.removerItem(produtoId);
        return carrinhoRepository.save(carrinho);
    }

    /**
     * Adiciona um cupom ao carrinho.
     *
     * O cupom pode ser de dois tipos:
     * - PROMOCIONAL: Desconto aplicado sobre o valor dos itens
     * - TROCA: Crédito que pode ser usado como parte do pagamento
     *
     * @param clienteId ID do cliente
     * @param codigoCupom Código do cupom a ser aplicado
     * @return Carrinho atualizado
     * @throws RuntimeException Se cupom não encontrado, inválido, ou não pertence ao cliente
     */
    public Carrinho adicionarCupom(Long clienteId, String codigoCupom) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        // Busca o cupom pelo código (apenas cupons ativos)
        Cupom cupom = cupomRepository.findByCodigoAndAtivoTrue(codigoCupom)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado ou inválido: " + codigoCupom));

        // ===== VALIDAÇÕES ESPECÍFICAS POR TIPO DE CUPOM =====

        // Cupons de troca são pessoais e intransferíveis
        if (cupom.getTipo() == TipoCupom.TROCA &&
                (cupom.getCliente() == null || !cupom.getCliente().getId().equals(clienteId))) {
            throw new RuntimeException("Este cupom de troca não pertence a este cliente");
        }

        // Verifica se o cupom está válido (ativo, não expirado, com usos disponíveis)
        if (!cupom.isValido()) {
            throw new RuntimeException("Cupom expirado ou sem usos disponíveis");
        }

        // Delega a adição do cupom para a entidade Carrinho
        // A entidade pode ter lógicas como: limitar a 1 cupom promocional por carrinho
        carrinho.adicionarCupom(cupom);

        return carrinhoRepository.save(carrinho);
    }

    /**
     * Remove um cupom do carrinho.
     *
     * @param clienteId ID do cliente
     * @param cupomId ID do cupom a ser removido
     * @return Carrinho atualizado
     * @throws RuntimeException Se cupom não encontrado
     */
    public Carrinho removerCupom(Long clienteId, Long cupomId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        Cupom cupom = cupomRepository.findById(cupomId)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado"));

        carrinho.removerCupom(cupom);
        return carrinhoRepository.save(carrinho);
    }

    /**
     * Limpa o carrinho, removendo todos os itens e cupons.
     * O carrinho em si não é deletado, apenas esvaziado.
     *
     * @param clienteId ID do cliente
     */
    public void limparCarrinho(Long clienteId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        // Limpa todas as coleções do carrinho
        carrinho.getItens().clear();      // Remove todos os itens
        carrinho.getCupons().clear();     // Remove todos os cupons
        carrinho.setValorFrete(BigDecimal.ZERO);  // Zera o frete
        carrinho.setTipoFreteSelecionado(null);   // Remove o tipo de frete

        carrinhoRepository.save(carrinho);
    }

    /**
     * Remove permanentemente o carrinho do cliente do banco de dados.
     * Diferente de limparCarrinho(), este método deleta o registro.
     *
     * @param clienteId ID do cliente
     */
    public void limparCarrinhoERemover(Long clienteId) {
        // Deleta diretamente pelo ID do cliente
        carrinhoRepository.deleteByClienteId(clienteId);
    }

    // ===== NOVO: ATUALIZAR FRETE POR ENDEREÇO =====

    /**
     * Calcula e atualiza o valor do frete baseado no endereço selecionado.
     *
     * O valor do frete é calculado com base na UF (estado) do endereço,
     * pois diferentes regiões podem ter custos de frete diferentes.
     *
     * @param clienteId ID do cliente
     * @param enderecoId ID do endereço de entrega selecionado
     * @return Carrinho atualizado com o valor do frete
     * @throws RuntimeException Se endereço não encontrado
     */
    public Carrinho atualizarFretePorEndereco(Long clienteId, Long enderecoId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        // Busca o endereço no banco de dados
        Endereco endereco = enderecoRepository.findById(enderecoId)
                .orElseThrow(() -> new RuntimeException("Endereço não encontrado"));

        // Calcula o frete baseado na UF (Unidade Federativa) do endereço
        // Ex: SP pode ter frete mais barato que AM
        BigDecimal frete = freteService.calcularFrete(endereco.getUf());

        // Atualiza o carrinho com o valor do frete
        carrinho.setValorFrete(frete);
        carrinho.setTipoFreteSelecionado("PADRÃO"); // Pode ser expandido para outros tipos (EXPRESSO, ECONÔMICO)

        return carrinhoRepository.save(carrinho);
    }

    // ===== MÉTODOS DE CONSULTA =====

    /**
     * Busca o carrinho do cliente com seus itens carregados (fetch).
     *
     * Este método utiliza uma query específica que faz JOIN FETCH
     * para evitar o problema N+1 de consultas.
     *
     * @param clienteId ID do cliente
     * @return Carrinho com itens carregados
     */
    public Carrinho buscarComItens(Long clienteId) {
        return carrinhoRepository.buscarComItensPorClienteId(clienteId)
                .orElseGet(() -> obterCarrinhoDoCliente(clienteId));
    }

    /**
     * Retorna a quantidade total de itens no carrinho.
     *
     * @param clienteId ID do cliente
     * @return Número total de itens (soma das quantidades, não de produtos únicos)
     */
    public int getQuantidadeItens(Long clienteId) {
        return obterCarrinhoDoCliente(clienteId).getQuantidadeItens();
    }

    /**
     * Retorna o subtotal do carrinho (soma dos preços dos itens sem desconto).
     *
     * @param clienteId ID do cliente
     * @return Subtotal dos produtos
     */
    public BigDecimal getSubtotal(Long clienteId) {
        return obterCarrinhoDoCliente(clienteId).getSubtotal();
    }

    /**
     * Retorna o total do carrinho (subtotal + frete - descontos).
     *
     * @param clienteId ID do cliente
     * @return Valor final a ser pago
     */
    public BigDecimal getTotal(Long clienteId) {
        return obterCarrinhoDoCliente(clienteId).getTotal();
    }
}