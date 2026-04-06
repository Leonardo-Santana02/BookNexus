package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.venda.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {
    List<ItemPedido> findByPedidoId(Long pedidoId);
    List<ItemPedido> findByProdutoId(Long produtoId);

    @Query("SELECT SUM(i.quantidade) FROM ItemPedido i WHERE i.produto.id = :produtoId")
    Long somarQuantidadeVendidaPorProduto(@Param("produtoId") Long produtoId);
}