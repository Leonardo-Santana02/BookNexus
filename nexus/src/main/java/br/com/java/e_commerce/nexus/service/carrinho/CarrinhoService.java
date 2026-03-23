package br.com.java.e_commerce.nexus.service.carrinho;

import br.com.java.e_commerce.nexus.model.carrinho.Carrinho;
import br.com.java.e_commerce.nexus.model.carrinho.ItemCarrinho;
import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.model.produto.Produto;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.repository.carrinho.CarrinhoRepository;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.produto.ProdutoRepository;
import br.com.java.e_commerce.nexus.repository.venda.CupomRepository;
import br.com.java.e_commerce.nexus.service.exception.EstoqueInsuficienteException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class CarrinhoService {

    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final CupomRepository cupomRepository;

    public CarrinhoService(CarrinhoRepository carrinhoRepository,
                           ClienteRepository clienteRepository,
                           ProdutoRepository produtoRepository,
                           CupomRepository cupomRepository) {
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.cupomRepository = cupomRepository;
    }

    // ===== MÉTODOS PRINCIPAIS =====

    public Carrinho obterCarrinhoDoCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado com ID: " + clienteId));

        return carrinhoRepository.findByClienteId(clienteId)
                .orElseGet(() -> {
                    Carrinho novoCarrinho = new Carrinho();
                    novoCarrinho.setCliente(cliente);
                    return carrinhoRepository.save(novoCarrinho);
                });
    }

    public Carrinho adicionarItem(Long clienteId, Long produtoId, int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        }

        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com ID: " + produtoId));

        // Verificar disponibilidade em estoque
        if (!produto.temEstoque(quantidade)) {
            throw new EstoqueInsuficienteException(
                    "Estoque insuficiente para o livro: " + produto.getTitulo() +
                            ". Disponível: " + produto.getEstoque()
            );
        }

        // CORREÇÃO: passar o objeto produto, não o ID
        carrinho.adicionarItem(produto, quantidade);
        return carrinhoRepository.save(carrinho);
    }

    public Carrinho alterarQuantidadeItem(Long clienteId, Long produtoId, int novaQuantidade) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        if (novaQuantidade <= 0) {
            carrinho.removerItem(produtoId);
        } else {
            // Verificar estoque se for aumentar quantidade
            ItemCarrinho itemExistente = carrinho.getItens().stream()
                    .filter(item -> item.getProduto().getId().equals(produtoId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Item não encontrado no carrinho"));

            if (novaQuantidade > itemExistente.getQuantidade()) {
                Produto produto = itemExistente.getProduto();
                int diferenca = novaQuantidade - itemExistente.getQuantidade();
                if (!produto.temEstoque(diferenca)) {
                    throw new EstoqueInsuficienteException(
                            "Estoque insuficiente para aumentar quantidade. Disponível: " + produto.getEstoque()
                    );
                }
            }

            carrinho.alterarQuantidadeItem(produtoId, novaQuantidade);
        }

        return carrinhoRepository.save(carrinho);
    }

    public Carrinho removerItem(Long clienteId, Long produtoId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);
        carrinho.removerItem(produtoId);
        return carrinhoRepository.save(carrinho);
    }

    public Carrinho adicionarCupom(Long clienteId, String codigoCupom) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        Cupom cupom = cupomRepository.findByCodigoAndAtivoTrue(codigoCupom)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado ou inválido: " + codigoCupom));

        // Validar se o cupom pertence ao cliente (se for cupom de troca)
        if (cupom.getTipo() == TipoCupom.TROCA &&
                (cupom.getCliente() == null || !cupom.getCliente().getId().equals(clienteId))) {
            throw new RuntimeException("Este cupom de troca não pertence a este cliente");
        }

        // Validar se o cupom é aplicável (não expirado, não usado)
        if (!cupom.isValido()) {
            throw new RuntimeException("Cupom expirado ou sem usos disponíveis");
        }

        carrinho.adicionarCupom(cupom);
        return carrinhoRepository.save(carrinho);
    }

    public Carrinho removerCupom(Long clienteId, Long cupomId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);

        Cupom cupom = cupomRepository.findById(cupomId)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado"));

        carrinho.removerCupom(cupom);
        return carrinhoRepository.save(carrinho);
    }

    public void limparCarrinho(Long clienteId) {
        Carrinho carrinho = obterCarrinhoDoCliente(clienteId);
        carrinho.getItens().clear();
        carrinho.getCupons().clear();
        carrinho.setValorFrete(BigDecimal.ZERO);
        carrinho.setTipoFreteSelecionado(null);
        carrinhoRepository.save(carrinho);
    }

    public void limparCarrinhoERemover(Long clienteId) {
        carrinhoRepository.deleteByClienteId(clienteId);
    }

    // ===== MÉTODOS DE CONSULTA =====

    public Carrinho buscarComItens(Long clienteId) {
        return carrinhoRepository.buscarComItensPorClienteId(clienteId)
                .orElseGet(() -> obterCarrinhoDoCliente(clienteId));
    }

    public int getQuantidadeItens(Long clienteId) {
        return obterCarrinhoDoCliente(clienteId).getQuantidadeItens();
    }

    public BigDecimal getSubtotal(Long clienteId) {
        return obterCarrinhoDoCliente(clienteId).getSubtotal();
    }

    public BigDecimal getTotal(Long clienteId) {
        return obterCarrinhoDoCliente(clienteId).getTotal();
    }
}

