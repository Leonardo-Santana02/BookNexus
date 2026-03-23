package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CupomRepository extends JpaRepository<Cupom, Long> {

    Optional<Cupom> findByCodigoAndAtivoTrue(String codigo);

    List<Cupom> findByClienteAndAtivoTrueOrderByDataValidadeDesc(Cliente cliente);

    List<Cupom> findByTipoAndAtivoTrueOrderByDataValidadeDesc(TipoCupom tipo);

    @Query("SELECT c FROM Cupom c WHERE c.ativo = true AND c.dataValidade > CURRENT_TIMESTAMP " +
            "AND (c.cliente IS NULL OR c.cliente = :cliente)")
    List<Cupom> findCuponsValidosParaCliente(@Param("cliente") Cliente cliente);

    List<Cupom> findByClienteAndTipoAndAtivoTrue(Cliente cliente, TipoCupom tipo);

    long countByClienteAndTipoAndAtivoTrue(Cliente cliente, TipoCupom tipo);

    void deleteByCliente(Cliente cliente);

    @Query("SELECT c FROM Cupom c WHERE c.codigo = :codigo AND c.ativo = true " +
            "AND c.dataValidade > CURRENT_TIMESTAMP " +
            "AND (c.cliente IS NULL OR c.cliente.id = :clienteId)")
    Optional<Cupom> findValidoPorCodigoECliente(@Param("codigo") String codigo,
                                                @Param("clienteId") Long clienteId);
}