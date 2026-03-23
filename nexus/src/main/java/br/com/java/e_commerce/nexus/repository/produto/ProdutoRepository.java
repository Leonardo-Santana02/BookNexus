package br.com.java.e_commerce.nexus.repository.produto;

import br.com.java.e_commerce.nexus.model.produto.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // Busca por ISBN (único)
    Optional<Produto> findByIsbn(String isbn);

    // Busca por título (case-insensitive, busca parcial)
    List<Produto> findByTituloContainingIgnoreCase(String titulo);

    // Busca por autor (case-insensitive, busca parcial)
    List<Produto> findByAutorContainingIgnoreCase(String autor);

    // Busca por editora
    List<Produto> findByEditoraContainingIgnoreCase(String editora);

    // Busca por gênero
    List<Produto> findByGenero(String genero);

    // Busca por gênero e subgênero
    List<Produto> findByGeneroAndSubGenero(String genero, String subGenero);

    // Busca por faixa de preço
    List<Produto> findByPrecoBetween(BigDecimal precoMin, BigDecimal precoMax);

    // Busca por ano de publicação
    List<Produto> findByAnoPublicacao(Integer ano);

    // Busca por ano de publicação entre
    List<Produto> findByAnoPublicacaoBetween(Integer anoInicial, Integer anoFinal);

    // Busca apenas produtos ativos
    List<Produto> findByAtivoTrue();

    // Busca apenas produtos inativos
    List<Produto> findByAtivoFalse();

    // Busca produtos com estoque baixo (menor que o limite)
    List<Produto> findByEstoqueLessThan(Integer limite);

    // Busca por título, autor ou sinopse (busca global)
    @Query("SELECT p FROM Produto p WHERE " +
            "LOWER(p.titulo) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "LOWER(p.autor) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "LOWER(p.sinopse) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
            "LOWER(p.isbn) LIKE LOWER(CONCAT('%', :termo, '%'))")
    List<Produto> buscaGlobal(@Param("termo") String termo);

    // Busca com múltiplos filtros
    @Query("SELECT p FROM Produto p WHERE " +
            "(:titulo IS NULL OR LOWER(p.titulo) LIKE LOWER(CONCAT('%', :titulo, '%'))) AND " +
            "(:autor IS NULL OR LOWER(p.autor) LIKE LOWER(CONCAT('%', :autor, '%'))) AND " +
            "(:editora IS NULL OR LOWER(p.editora) LIKE LOWER(CONCAT('%', :editora, '%'))) AND " +
            "(:genero IS NULL OR p.genero = :genero) AND " +
            "(:precoMin IS NULL OR p.preco >= :precoMin) AND " +
            "(:precoMax IS NULL OR p.preco <= :precoMax) AND " +
            "(:anoMin IS NULL OR p.anoPublicacao >= :anoMin) AND " +
            "(:anoMax IS NULL OR p.anoPublicacao <= :anoMax) AND " +
            "(:ativo IS NULL OR p.ativo = :ativo)")
    List<Produto> buscarComFiltros(
            @Param("titulo") String titulo,
            @Param("autor") String autor,
            @Param("editora") String editora,
            @Param("genero") String genero,
            @Param("precoMin") BigDecimal precoMin,
            @Param("precoMax") BigDecimal precoMax,
            @Param("anoMin") Integer anoMin,
            @Param("anoMax") Integer anoMax,
            @Param("ativo") Boolean ativo
    );

    // Contar produtos por gênero
    Long countByGenero(String genero);

    // Contar produtos por editora
    Long countByEditora(String editora);

    // Verificar se existe produto com ISBN ignorando ID (para edição)
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Produto p " +
            "WHERE p.isbn = :isbn AND p.id != :id")
    boolean existsByIsbnAndIdNot(@Param("isbn") String isbn, @Param("id") Long id);

    // Buscar livros mais recentes (últimos 10)
    List<Produto> findTop10ByOrderByDataCadastroDesc();

    // Buscar livros mais vendidos (simulado - você pode adaptar depois com vendas reais)
    @Query("SELECT p FROM Produto p ORDER BY p.id DESC")
    List<Produto> findMaisVendidos();
}