package br.com.java.e_commerce.nexus.repository.cliente;

import br.com.java.e_commerce.nexus.model.cliente.CartaoCredito;
import br.com.java.e_commerce.nexus.model.enums.BandeiraCartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartaoCreditoRepository extends JpaRepository<CartaoCredito, Long> {

    // Buscar cartões de um cliente específico
    List<CartaoCredito> findByClienteId(Long clienteId);

    // Buscar por bandeira
    List<CartaoCredito> findByBandeira(BandeiraCartao bandeira);

    // Buscar cartão preferencial de um cliente
    CartaoCredito findByClienteIdAndPreferencialTrue(Long clienteId);

    // Buscar pelos 4 últimos dígitos (útil para listagens)
    @Query("SELECT c FROM CartaoCredito c WHERE SUBSTRING(c.numeroCartao, -4) = :ultimosDigitos")
    List<CartaoCredito> findByUltimosDigitos(@Param("ultimosDigitos") String ultimosDigitos);

    // Verificar se cliente já tem cartão preferencial
    boolean existsByClienteIdAndPreferencialTrue(Long clienteId);

    // Buscar cartões de um cliente por bandeira
    List<CartaoCredito> findByClienteIdAndBandeira(Long clienteId, BandeiraCartao bandeira);
}