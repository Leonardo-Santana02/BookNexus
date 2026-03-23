package br.com.java.e_commerce.nexus.repository.cliente;

import br.com.java.e_commerce.nexus.model.cliente.Telefone;
import br.com.java.e_commerce.nexus.model.enums.TipoTelefone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TelefoneRepository extends JpaRepository<Telefone, Long> {

    // Buscar telefones de um cliente específico
    List<Telefone> findByClienteId(Long clienteId);

    // Buscar por tipo (FIXO/CELULAR)
    List<Telefone> findByTipo(TipoTelefone tipo);

    // Buscar por DDD
    List<Telefone> findByDdd(String ddd);

    // Buscar por número
    List<Telefone> findByNumero(String numero);

    // Buscar por DDD e número
    Telefone findByDddAndNumero(String ddd, String numero);

    // Buscar telefones de um cliente por tipo
    List<Telefone> findByClienteIdAndTipo(Long clienteId, TipoTelefone tipo);
}