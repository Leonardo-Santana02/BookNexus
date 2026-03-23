package br.com.java.e_commerce.nexus.repository.cliente;

import br.com.java.e_commerce.nexus.model.cliente.Endereco;
import br.com.java.e_commerce.nexus.model.enums.UF;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    // Buscar endereços de um cliente específico
    List<Endereco> findByClienteId(Long clienteId);

    // Buscar endereço de entrega de um cliente
    Endereco findByClienteIdAndEnderecoEntregaTrue(Long clienteId);

    // Buscar endereço de cobrança de um cliente
    Endereco findByClienteIdAndEnderecoCobrancaTrue(Long clienteId);

    // Buscar por CEP
    List<Endereco> findByCep(String cep);

    // Buscar por cidade
    List<Endereco> findByCidadeContainingIgnoreCase(String cidade);

    // Buscar por UF
    List<Endereco> findByUf(UF uf);

    // Verificar se cliente já tem endereço de entrega
    boolean existsByClienteIdAndEnderecoEntregaTrue(Long clienteId);

    // Verificar se cliente já tem endereço de cobrança
    boolean existsByClienteIdAndEnderecoCobrancaTrue(Long clienteId);
}