package br.com.java.e_commerce.nexus.repository.venda;

import br.com.java.e_commerce.nexus.model.venda.PagamentoCartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PagamentoCartaoRepository extends JpaRepository<PagamentoCartao, Long> {
}