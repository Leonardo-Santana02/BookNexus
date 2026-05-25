package br.com.java.e_commerce.nexus.service.venda;

import br.com.java.e_commerce.nexus.model.venda.ItemPedido;
import br.com.java.e_commerce.nexus.model.venda.Pedido;
import br.com.java.e_commerce.nexus.repository.venda.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnaliseService {

    private final PedidoRepository pedidoRepository;

    public AnaliseService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    /**
     * Gera dados para gráfico de linha: Vendas por Categoria (Gênero) ao longo do tempo
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Map com labels (datas) e datasets (categorias com valores por data)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> gerarSeriesPorCategoria(LocalDateTime inicio, LocalDateTime fim) {
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);
        List<String> labels = gerarListaDatas(inicio.toLocalDate(), fim.toLocalDate());

        // Estrutura: categoria -> data -> valor
        Map<String, Map<String, BigDecimal>> acumulado = new TreeMap<>();

        for (Pedido pedido : pedidos) {
            String data = pedido.getDataCriacao().toLocalDate().toString();
            BigDecimal subtotalPedido = pedido.getSubtotal();
            BigDecimal descontoTotal = pedido.getDescontoPromocional() != null ?
                    pedido.getDescontoPromocional() : BigDecimal.ZERO;

            // Garantir que desconto não seja negativo
            if (descontoTotal.compareTo(BigDecimal.ZERO) < 0) {
                descontoTotal = BigDecimal.ZERO;
            }

            for (ItemPedido item : pedido.getItens()) {
                String categoria = item.getProduto().getGenero(); // BookNexus usa "genero"
                BigDecimal valorBrutoItem = item.getPrecoTotal();
                BigDecimal valorLiquidoItem = valorBrutoItem;

                // Rateio proporcional do desconto (se houver)
                if (subtotalPedido.compareTo(BigDecimal.ZERO) > 0 && descontoTotal.compareTo(BigDecimal.ZERO) > 0) {
                    valorLiquidoItem = valorBrutoItem.subtract(
                            descontoTotal.multiply(valorBrutoItem)
                                    .divide(subtotalPedido, 2, RoundingMode.HALF_UP)
                    );
                    if (valorLiquidoItem.compareTo(BigDecimal.ZERO) < 0) {
                        valorLiquidoItem = BigDecimal.ZERO;
                    }
                }

                acumulado.computeIfAbsent(categoria, c -> inicializarMapaDatas(labels))
                        .merge(data, valorLiquidoItem, BigDecimal::add);
            }
        }

        List<Map<String, Object>> datasets = montarDatasets(acumulado, labels, false);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("labels", labels);
        resultado.put("datasets", datasets);
        return resultado;
    }

    /**
     * Gera dados para gráfico de linha: Volume de Vendas por Produto ao longo do tempo
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Map com labels (datas) e datasets (produtos com quantidades por data)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> gerarSeriesVolumePorProduto(LocalDateTime inicio, LocalDateTime fim) {
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);
        List<String> labels = gerarListaDatas(inicio.toLocalDate(), fim.toLocalDate());

        // Estrutura: produto -> data -> quantidade
        Map<String, Map<String, BigDecimal>> acumulado = new TreeMap<>();

        for (Pedido pedido : pedidos) {
            String data = pedido.getDataCriacao().toLocalDate().toString();
            for (ItemPedido item : pedido.getItens()) {
                String produtoNome = item.getProduto().getTitulo(); // BookNexus usa "titulo"
                BigDecimal quantidade = BigDecimal.valueOf(item.getQuantidade());

                acumulado.computeIfAbsent(produtoNome, c -> inicializarMapaDatas(labels))
                        .merge(data, quantidade, BigDecimal::add);
            }
        }

        // Limita a 10 produtos mais vendidos para não sobrecarregar o gráfico
        Map<String, Map<String, BigDecimal>> topProdutos = limitarTopProdutos(acumulado, 10);

        List<Map<String, Object>> datasets = montarDatasets(topProdutos, labels, true);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("labels", labels);
        resultado.put("datasets", datasets);
        return resultado;
    }

    /**
     * Limita o número de produtos exibidos no gráfico aos mais vendidos
     *
     * @param acumulado Mapa de produtos com dados por data
     * @param limite Número máximo de produtos a retornar
     * @return Mapa com apenas os produtos mais vendidos
     */
    private Map<String, Map<String, BigDecimal>> limitarTopProdutos(
            Map<String, Map<String, BigDecimal>> acumulado, int limite) {

        // Calcula total de vendas por produto
        Map<String, BigDecimal> totalPorProduto = new HashMap<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : acumulado.entrySet()) {
            BigDecimal total = entry.getValue().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalPorProduto.put(entry.getKey(), total);
        }

        // Ordena por total vendido e pega os top N
        List<Map.Entry<String, BigDecimal>> sorted = new ArrayList<>(totalPorProduto.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        Map<String, Map<String, BigDecimal>> topProdutos = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(limite, sorted.size()); i++) {
            String produtoNome = sorted.get(i).getKey();
            topProdutos.put(produtoNome, acumulado.get(produtoNome));
        }

        return topProdutos;
    }

    /**
     * Gera lista de todas as datas entre início e fim (inclusive)
     *
     * @param inicio Data inicial
     * @param fim Data final
     * @return Lista de strings no formato YYYY-MM-DD
     */
    private List<String> gerarListaDatas(LocalDate inicio, LocalDate fim) {
        List<String> datas = new ArrayList<>();
        LocalDate cursor = inicio;
        while (!cursor.isAfter(fim)) {
            datas.add(cursor.toString());
            cursor = cursor.plusDays(1);
        }
        return datas;
    }

    /**
     * Inicializa um mapa com todas as datas com valor zero
     *
     * @param labels Lista de datas
     * @return Mapa data -> BigDecimal.ZERO
     */
    private Map<String, BigDecimal> inicializarMapaDatas(List<String> labels) {
        Map<String, BigDecimal> mapa = new HashMap<>();
        for (String data : labels) {
            mapa.put(data, BigDecimal.ZERO);
        }
        return mapa;
    }

    /**
     * Monta a lista de datasets para o Chart.js
     *
     * @param origem Mapa: chave -> (data -> valor)
     * @param labels Lista de datas em ordem
     * @param isVolume Se true, muda propriedades visuais para volume de vendas
     * @return Lista de datasets formatada para Chart.js
     */
    private List<Map<String, Object>> montarDatasets(Map<String, Map<String, BigDecimal>> origem,
                                                     List<String> labels,
                                                     boolean isVolume) {
        List<Map<String, Object>> datasets = new ArrayList<>();

        // Paleta de cores para as diferentes séries
        String[] coresBase = {
                "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
                "#DDA0DD", "#98D8C8", "#F7B731", "#5B5B5B", "#FF8C42",
                "#6C5CE7", "#A8E6CF", "#FFD3B6", "#FF8B94", "#C7CEE6"
        };

        int index = 0;
        for (Map.Entry<String, Map<String, BigDecimal>> entry : origem.entrySet()) {
            Map<String, Object> dataset = new HashMap<>();
            dataset.put("label", entry.getKey());

            // Extrai valores na ordem das labels
            List<BigDecimal> valoresOrdenados = new ArrayList<>();
            for (String data : labels) {
                valoresOrdenados.add(entry.getValue().getOrDefault(data, BigDecimal.ZERO));
            }
            dataset.put("data", valoresOrdenados);

            String cor = coresBase[index % coresBase.length];
            dataset.put("borderColor", cor);
            // Para volume (quantidade), cor mais sólida; para valor, mais transparente
            dataset.put("backgroundColor", isVolume ? cor + "80" : cor + "20");
            dataset.put("fill", false);
            dataset.put("tension", 0.2);
            dataset.put("pointRadius", 4);
            dataset.put("pointHoverRadius", 6);
            dataset.put("borderWidth", 2);

            datasets.add(dataset);
            index++;
        }

        return datasets;
    }

    // ===== MÉTODOS PARA MÉTRICAS DO DASHBOARD =====

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
                .filter(Pedido::temItens)
                .map(Pedido::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Conta o número total de vendas (pedidos) no período especificado.
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Quantidade de pedidos válidos no período
     */
    @Transactional(readOnly = true)
    public Long contarVendas(LocalDateTime inicio, LocalDateTime fim) {
        return (long) pedidoRepository.findVendasPorPeriodo(inicio, fim).stream()
                .filter(Pedido::temItens)
                .count();
    }

    /**
     * Calcula o total de itens vendidos no período.
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Quantidade total de unidades vendidas
     */
    @Transactional(readOnly = true)
    public Long calcularTotalItensVendidos(LocalDateTime inicio, LocalDateTime fim) {
        return pedidoRepository.findVendasPorPeriodo(inicio, fim).stream()
                .filter(Pedido::temItens)
                .flatMap(p -> p.getItens().stream())
                .mapToLong(ItemPedido::getQuantidade)
                .sum();
    }

    /**
     * Calcula o ticket médio por pedido no período.
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @return Valor médio gasto por pedido
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularTicketMedio(LocalDateTime inicio, LocalDateTime fim) {
        BigDecimal totalVendas = calcularTotalVendas(inicio, fim);
        Long totalPedidos = contarVendas(inicio, fim);

        if (totalPedidos == 0) {
            return BigDecimal.ZERO;
        }

        return totalVendas.divide(BigDecimal.valueOf(totalPedidos), 2, RoundingMode.HALF_UP);
    }

    /**
     * Retorna os produtos mais vendidos no período (valor total em R$)
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @param limite Número máximo de produtos a retornar
     * @return Lista de mapas com produto, quantidade e valor total
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obterProdutosMaisVendidos(LocalDateTime inicio, LocalDateTime fim, int limite) {
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);

        // Estrutura: produtoId -> {nome, quantidadeTotal, valorTotal}
        Map<Long, Map<String, Object>> produtosMap = new HashMap<>();

        for (Pedido pedido : pedidos) {
            if (!pedido.temItens()) continue;

            for (ItemPedido item : pedido.getItens()) {
                Long produtoId = item.getProduto().getId();
                String produtoNome = item.getProduto().getTitulo();
                int quantidade = item.getQuantidade();
                BigDecimal valorItem = item.getPrecoTotal();

                produtosMap.computeIfAbsent(produtoId, id -> {
                    Map<String, Object> prod = new HashMap<>();
                    prod.put("nome", produtoNome);
                    prod.put("quantidade", 0L);
                    prod.put("valor", BigDecimal.ZERO);
                    return prod;
                });

                Map<String, Object> prod = produtosMap.get(produtoId);
                prod.put("quantidade", (Long) prod.get("quantidade") + quantidade);
                prod.put("valor", ((BigDecimal) prod.get("valor")).add(valorItem));
            }
        }

        // Converte para lista e ordena por valor total
        List<Map<String, Object>> lista = new ArrayList<>(produtosMap.values());
        lista.sort((a, b) -> ((BigDecimal) b.get("valor")).compareTo((BigDecimal) a.get("valor")));

        // Retorna apenas os top N
        return lista.subList(0, Math.min(limite, lista.size()));
    }

    /**
     * Retorna os gêneros mais vendidos no período (valor total em R$)
     *
     * @param inicio Data/hora inicial do período
     * @param fim Data/hora final do período
     * @param limite Número máximo de gêneros a retornar
     * @return Lista de mapas com gênero e valor total
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obterGenerosMaisVendidos(LocalDateTime inicio, LocalDateTime fim, int limite) {
        List<Pedido> pedidos = pedidoRepository.findVendasPorPeriodo(inicio, fim);

        Map<String, BigDecimal> generosMap = new HashMap<>();

        for (Pedido pedido : pedidos) {
            if (!pedido.temItens()) continue;

            BigDecimal subtotalPedido = pedido.getSubtotal();
            BigDecimal descontoTotal = pedido.getDescontoPromocional() != null ?
                    pedido.getDescontoPromocional() : BigDecimal.ZERO;

            if (descontoTotal.compareTo(BigDecimal.ZERO) < 0) {
                descontoTotal = BigDecimal.ZERO;
            }

            for (ItemPedido item : pedido.getItens()) {
                String genero = item.getProduto().getGenero();
                BigDecimal valorBrutoItem = item.getPrecoTotal();
                BigDecimal valorLiquidoItem = valorBrutoItem;

                // Rateio proporcional do desconto
                if (subtotalPedido.compareTo(BigDecimal.ZERO) > 0 && descontoTotal.compareTo(BigDecimal.ZERO) > 0) {
                    valorLiquidoItem = valorBrutoItem.subtract(
                            descontoTotal.multiply(valorBrutoItem)
                                    .divide(subtotalPedido, 2, RoundingMode.HALF_UP)
                    );
                    if (valorLiquidoItem.compareTo(BigDecimal.ZERO) < 0) {
                        valorLiquidoItem = BigDecimal.ZERO;
                    }
                }

                generosMap.merge(genero, valorLiquidoItem, BigDecimal::add);
            }
        }

        // Converte para lista e ordena por valor total
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : generosMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("genero", entry.getKey());
            item.put("valor", entry.getValue());
            lista.add(item);
        }

        lista.sort((a, b) -> ((BigDecimal) b.get("valor")).compareTo((BigDecimal) a.get("valor")));

        return lista.subList(0, Math.min(limite, lista.size()));
    }
}