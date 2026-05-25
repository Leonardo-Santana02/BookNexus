package br.com.java.e_commerce.nexus.repository.cliente;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.enums.Genero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    // ==================== MÉTODOS DE VERIFICAÇÃO DE EXISTÊNCIA ====================

    /**
     * Verifica se já existe um cliente cadastrado com o CPF informado.
     * Utilizado para garantir unicidade do CPF no sistema.
     *
     * O Spring Data JPA gera automaticamente a consulta:
     *
     * @param cpf CPF a ser verificado (já deve estar sem máscara)
     * @return true se já existe um cliente com este CPF, false caso contrário
     */
    boolean existsByCpf(String cpf);

    /**
     * Verifica se já existe um cliente cadastrado com o email informado.
     * Utilizado para garantir unicidade do email no sistema.
     *
     * @param email Email a ser verificado
     * @return true se já existe um cliente com este email, false caso contrário
     */
    boolean existsByEmail(String email);

    // ==================== MÉTODOS DE CONSULTA BÁSICA ====================
    // Estes métodos utilizam a convenção de nomenclatura do Spring Data JPA
    // para gerar consultas automaticamente.

    /**
     * Busca clientes pelo nome (busca parcial, case-insensitive).
     *
     * Consulta gerada:
     *
     * @param nome Nome ou parte do nome para busca
     * @return Lista de clientes que contêm o texto informado no nome
     */
    List<Cliente> findByNomeContainingIgnoreCase(String nome);

    /**
     * Busca clientes pelo email (busca parcial, case-insensitive).
     * Útil para autocomplete ou busca aproximada de emails.
     *
     * @param email Email ou parte do email para busca
     * @return Lista de clientes que contêm o texto informado no email
     */
    List<Cliente> findByEmailContainingIgnoreCase(String email);

    /**
     * Busca um cliente pelo CPF (busca exata).
     * Como CPF é único no sistema, o retorno é um único cliente (não uma lista).
     *
     * @param cpf CPF completo do cliente (já sem máscara)
     * @return Cliente encontrado ou null se não existir
     */
    Cliente findByCpf(String cpf);

    /**
     * Busca clientes pelo gênero.
     *
     * @param genero Gênero do cliente (MASCULINO, FEMININO, OUTRO)
     * @return Lista de clientes do gênero informado
     */
    List<Cliente> findByGenero(Genero genero);

    /**
     * Busca clientes pela data de nascimento (busca exata).
     *
     * @param dataNascimento Data de nascimento a ser pesquisada
     * @return Lista de clientes que nasceram nesta data
     */
    List<Cliente> findByDataNascimento(LocalDate dataNascimento);

    /**
     * Busca apenas clientes ativos (não inativados).
     * Utilizado para listagens que não devem incluir clientes excluídos logicamente.
     *
     * @return Lista de clientes com inativado = false
     */
    List<Cliente> findByInativadoFalse();

    /**
     * Busca apenas clientes inativados (exclusão lógica).
     * Útil para relatórios de clientes removidos ou para permitir reativação.
     *
     * @return Lista de clientes com inativado = true
     */
    List<Cliente> findByInativadoTrue();

    // ==================== CONSULTAS AVANÇADAS COM NATIVE QUERY ====================
    // Estas consultas são escritas em SQL nativo quando a complexidade excede
    // a capacidade das query methods ou JPQL.

    /**
     * NATIVE QUERY COMPLETA - Consulta avançada com suporte a múltiplos filtros.
     *
     * Esta é a consulta mais completa do sistema, permitindo combinar todos os
     * critérios de busca disponíveis:
     * - ID (busca exata)
     * - Nome (busca parcial)
     * - Email (busca parcial)
     * - CPF (busca exata)
     * - Gênero (busca exata)
     * - Data de nascimento (aceita dois formatos)
     * - Telefone (busca parcial, incluindo DDD)
     * - Status ativo/inativo
     *
     * Características técnicas:
     * - Utiliza LEFT JOIN para incluir clientes mesmo sem telefone
     * - DISTINCT evita duplicatas quando cliente tem múltiplos telefones
     * - ILIKE para busca case-insensitive em texto
     * - TO_CHAR para comparar datas em diferentes formatos
     * - CAST para converter ID string para bigint
     *
     * @param id ID do cliente (pode ser null para ignorar este filtro)
     * @param nome Nome ou parte do nome (case-insensitive)
     * @param email Email ou parte do email (case-insensitive)
     * @param cpf CPF completo (busca exata)
     * @param genero Gênero como String (será comparado com o enum)
     * @param dataNascimento Data de nascimento em formato DD/MM/AAAA ou YYYY-MM-DD
     * @param telefone Número de telefone ou parte dele (case-insensitive)
     * @param inativado Status de inativação (true=inativo, false=ativo, null=ignora)
     * @return Lista de clientes que atendem a todos os filtros fornecidos
     */
    @Query(value = "SELECT DISTINCT c.* FROM clientes c " +
            "LEFT JOIN telefones t ON c.id = t.cliente_id " +
            "WHERE " +
            "(:id IS NULL OR c.id = CAST(:id AS bigint)) AND " +
            "(:nome IS NULL OR c.nome ILIKE CONCAT('%', :nome, '%')) AND " +
            "(:email IS NULL OR c.email ILIKE CONCAT('%', :email, '%')) AND " +
            "(:cpf IS NULL OR c.cpf = :cpf) AND " +
            "(:genero IS NULL OR c.genero = :genero) AND " +
            "(:dataNascimento IS NULL OR TO_CHAR(c.data_nascimento, 'DD/MM/YYYY') = :dataNascimento OR " +
            "    TO_CHAR(c.data_nascimento, 'YYYY-MM-DD') = :dataNascimento) AND " +
            "(:telefone IS NULL OR t.numero ILIKE CONCAT('%', :telefone, '%')) AND " +
            "(:inativado IS NULL OR c.inativado = :inativado)",
            nativeQuery = true)
    List<Cliente> findByFiltrosCompleto(
            @Param("id") String id,
            @Param("nome") String nome,
            @Param("email") String email,
            @Param("cpf") String cpf,
            @Param("genero") String genero,
            @Param("dataNascimento") String dataNascimento,
            @Param("telefone") String telefone,
            @Param("inativado") Boolean inativado
    );

    /**
     * Query simplificada (mantida para compatibilidade com versões anteriores).
     *
     * Esta versão não inclui os filtros de ID, telefone e data de nascimento.
     * Foi mantida para não quebrar código existente que depende desta assinatura.
     *
     * Características:
     * - Busca apenas pelos campos básicos (nome, email, cpf, genero, status)
     * - Não faz JOIN com telefones, sendo mais eficiente quando não necessário
     *
     * @param nome Nome ou parte do nome (case-insensitive)
     * @param email Email ou parte do email (case-insensitive)
     * @param cpf CPF completo (busca exata)
     * @param genero Gênero como String
     * @param inativado Status de inativação
     * @return Lista de clientes que atendem aos filtros básicos
     */
    @Query(value = "SELECT * FROM clientes WHERE " +
            "(:nome IS NULL OR nome ILIKE CONCAT('%', :nome, '%')) AND " +
            "(:email IS NULL OR email ILIKE CONCAT('%', :email, '%')) AND " +
            "(:cpf IS NULL OR cpf = :cpf) AND " +
            "(:genero IS NULL OR genero = :genero) AND " +
            "(:inativado IS NULL OR inativado = :inativado)",
            nativeQuery = true)
    List<Cliente> findByFiltros(
            @Param("nome") String nome,
            @Param("email") String email,
            @Param("cpf") String cpf,
            @Param("genero") String genero,
            @Param("inativado") Boolean inativado
    );

    // ==================== MÉTODOS PARA VALIDAÇÃO EM ATUALIZAÇÕES ====================
    // Estes métodos permitem verificar unicidade durante edições, ignorando
    // o próprio registro que está sendo editado.

    /**
     * Verifica se existe cliente com o mesmo CPF, ignorando o cliente com o ID informado.
     *
     * Utilizado durante a atualização de cliente para evitar duplicidade de CPF
     * sem gerar falso positivo com o próprio cliente que está sendo editado.
     *
     * Esta consulta utiliza JPQL (Java Persistence Query Language) em vez de SQL nativo,
     * permitindo maior portabilidade entre bancos de dados.
     *
     * @param cpf CPF a ser verificado
     * @param id ID do cliente que está sendo editado (será ignorado na verificação)
     * @return true se existe outro cliente com o mesmo CPF, false caso contrário
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Cliente c WHERE c.cpf = :cpf AND c.id != :id")
    boolean existsByCpfAndIdNot(@Param("cpf") String cpf, @Param("id") Long id);

    /**
     * Verifica se existe cliente com o mesmo email, ignorando o cliente com o ID informado.
     *
     * Utilizado durante a atualização de cliente para evitar duplicidade de email
     * sem gerar falso positivo com o próprio cliente que está sendo editado.
     *
     * @param email Email a ser verificado
     * @param id ID do cliente que está sendo editado (será ignorado na verificação)
     * @return true se existe outro cliente com o mesmo email, false caso contrário
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Cliente c WHERE c.email = :email AND c.id != :id")
    boolean existsByEmailAndIdNot(@Param("email") String email, @Param("id") Long id);
}