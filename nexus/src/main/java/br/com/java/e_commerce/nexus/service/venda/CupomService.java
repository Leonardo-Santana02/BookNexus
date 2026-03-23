package br.com.java.e_commerce.nexus.service.venda;

import br.com.java.e_commerce.nexus.model.cliente.Cliente;
import br.com.java.e_commerce.nexus.model.venda.Cupom;
import br.com.java.e_commerce.nexus.model.enums.TipoCupom;
import br.com.java.e_commerce.nexus.repository.cliente.ClienteRepository;
import br.com.java.e_commerce.nexus.repository.venda.CupomRepository;
import br.com.java.e_commerce.nexus.service.exception.CupomInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CupomService {

    private final CupomRepository cupomRepository;
    private final ClienteRepository clienteRepository;

    public CupomService(CupomRepository cupomRepository, ClienteRepository clienteRepository) {
        this.cupomRepository = cupomRepository;
        this.clienteRepository = clienteRepository;
    }

    // ===== CRIAÇÃO DE CUPONS =====

    public Cupom criarCupomPromocional(String codigo, BigDecimal valor,
                                       LocalDateTime dataValidade, String descricao) {
        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo.toUpperCase());
        cupom.setTipo(TipoCupom.PROMOCIONAL);
        cupom.setValor(valor);
        cupom.setDataValidade(dataValidade);
        cupom.setDescricao(descricao);
        cupom.setMaximoUsos(100); // Limite padrão
        return cupomRepository.save(cupom);
    }

    public Cupom criarCupomPromocionalParaCliente(Long clienteId, BigDecimal valor, String descricao) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        String codigo = "PROMO-" + valor.stripTrailingZeros().toPlainString()
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        cupom.setTipo(TipoCupom.PROMOCIONAL);
        cupom.setValor(valor);
        cupom.setCliente(cliente);
        cupom.setDataValidade(LocalDateTime.now().plusMonths(3)); // 3 meses
        cupom.setDescricao(descricao);
        cupom.setMaximoUsos(1); // Uso único

        return cupomRepository.save(cupom);
    }

    public Cupom criarCupomTroca(Long clienteId, BigDecimal valor, String descricao) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        String codigo = "TROCA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Cupom cupom = new Cupom();
        cupom.setCodigo(codigo);
        cupom.setTipo(TipoCupom.TROCA);
        cupom.setValor(valor);
        cupom.setCliente(cliente);
        cupom.setDataValidade(LocalDateTime.now().plusMonths(6)); // 6 meses
        cupom.setDescricao(descricao);
        cupom.setMaximoUsos(1); // Uso único

        return cupomRepository.save(cupom);
    }

    // ===== VALIDAÇÃO E CONSULTA =====

    public Cupom validarCupom(String codigo, Long clienteId) {
        Cupom cupom = cupomRepository.findValidoPorCodigoECliente(codigo, clienteId)
                .orElseThrow(() -> new CupomInvalidoException(
                        "Cupom inválido, expirado ou não disponível para este cliente"));

        return cupom;
    }

    public Optional<Cupom> buscarPorCodigo(String codigo) {
        return cupomRepository.findByCodigoAndAtivoTrue(codigo);
    }

    public List<Cupom> buscarCuponsValidosParaCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));

        return cupomRepository.findCuponsValidosParaCliente(cliente);
    }

    public List<Cupom> buscarCuponsTrocaAtivosCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        return cupomRepository.findByClienteAndTipoAndAtivoTrue(cliente, TipoCupom.TROCA);
    }

    public List<Cupom> buscarCuponsPromocionaisAtivosCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        return cupomRepository.findByClienteAndTipoAndAtivoTrue(cliente, TipoCupom.PROMOCIONAL);
    }

    public boolean possuiPromocionaisAtivos(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        return cupomRepository.countByClienteAndTipoAndAtivoTrue(cliente, TipoCupom.PROMOCIONAL) > 0;
    }

    // ===== CONSUMO E GERENCIAMENTO =====

    @Transactional
    public void consumirCupom(Long cupomId) {
        Cupom cupom = cupomRepository.findById(cupomId)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado"));
        cupom.consumir();
        cupomRepository.save(cupom);
    }

    @Transactional
    public void desativarCupom(Long cupomId) {
        Cupom cupom = cupomRepository.findById(cupomId)
                .orElseThrow(() -> new RuntimeException("Cupom não encontrado"));
        cupom.setAtivo(false);
        cupomRepository.save(cupom);
    }

    public void removerTodosDoCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        cupomRepository.deleteByCliente(cliente);
    }

    // ===== CRUD BÁSICO =====

    public List<Cupom> listarTodos() {
        return cupomRepository.findAll();
    }

    public Optional<Cupom> buscarPorId(Long id) {
        return cupomRepository.findById(id);
    }

    public Cupom salvar(Cupom cupom) {
        return cupomRepository.save(cupom);
    }
}