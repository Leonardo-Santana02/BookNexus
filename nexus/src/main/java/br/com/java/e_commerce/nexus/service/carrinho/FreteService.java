<<<<<<< HEAD
    package br.com.java.e_commerce.nexus.service.carrinho;
    
    import br.com.java.e_commerce.nexus.model.enums.UF;
    import org.springframework.stereotype.Service;
    
    import java.math.BigDecimal;
    import java.util.EnumMap;
    import java.util.Map;
    
    /**
     * Serviço responsável por calcular o valor do frete com base na Unidade Federativa (UF)
     * do endereço de entrega do cliente.
     *
     * Este serviço implementa uma tabela de fretes regionalizada, onde cada estado
     * (ou região) tem um valor de frete pré-definido. Em um sistema real, esta lógica
     * poderia ser expandida para integrar com APIs de transportadoras como Correios,
     * JadLog, Melhor Envio, etc.
     *
     * @Service: Marca a classe como um bean Spring gerenciado.
     *
     * NOTA: Esta classe NÃO tem a anotação @Transactional porque:
     * 1. Ela não realiza operações de banco de dados
     * 2. Apenas faz cálculos em memória usando uma tabela estática
     * 3. Transações seriam desnecessárias e adicionariam overhead desnecessário
     */
    @Service
    public class FreteService {
    
        /**
         * Tabela de fretes por Unidade Federativa (UF).
         *
         * Utilizamos EnumMap por ser altamente eficiente para chaves do tipo Enum.
         * O EnumMap é implementado internamente como um array, oferecendo performance O(1)
         * para operações de busca, inserção e remoção.
         *
         * Map<UF, BigDecimal> - Cada UF mapeia para um valor de frete em reais (R$)
         */
        private static final Map<UF, BigDecimal> TABELA_FRETE = new EnumMap<>(UF.class);
    
        /**
         * Bloco estático de inicialização da tabela de fretes.
         *
         * Este bloco é executado uma única vez quando a classe é carregada pela JVM,
         * populando o mapa com todos os valores de frete por estado/região.
         *
         * A tabela segue uma lógica regionalizada onde:
         * - Sudeste (especialmente SP) tem frete mais barato (centro de distribuição)
         * - Regiões mais distantes têm fretes progressivamente mais caros
         * - Norte tem o frete mais caro devido à logística mais complexa
         */
        static {
            // ===== REGIÃO SUDESTE =====
            // São Paulo - Estado com maior concentração de centros de distribuição
            // Frete mais barato devido à proximidade com os armazéns principais
            TABELA_FRETE.put(UF.SP, new BigDecimal("10.00"));
    
            // Demais estados do Sudeste
            // RJ, MG e ES têm frete ligeiramente mais caro que SP
            TABELA_FRETE.put(UF.RJ, new BigDecimal("15.00"));  // Rio de Janeiro
            TABELA_FRETE.put(UF.MG, new BigDecimal("15.00"));  // Minas Gerais
            TABELA_FRETE.put(UF.ES, new BigDecimal("15.00"));  // Espírito Santo
    
            // ===== REGIÃO SUL =====
            // PR, SC, RS - Frete intermediário
            // Região Sul tem boa infraestrutura logística, mas distante do eixo SP-RJ
            TABELA_FRETE.put(UF.PR, new BigDecimal("18.00"));  // Paraná
            TABELA_FRETE.put(UF.SC, new BigDecimal("18.00"));  // Santa Catarina
            TABELA_FRETE.put(UF.RS, new BigDecimal("18.00"));  // Rio Grande do Sul
    
            // ===== REGIÃO CENTRO-OESTE =====
            // DF, GO, MT, MS - Frete mais elevado
            // Região com menor densidade populacional e maior distância dos centros
            TABELA_FRETE.put(UF.DF, new BigDecimal("20.00"));  // Distrito Federal (Brasília)
            TABELA_FRETE.put(UF.GO, new BigDecimal("20.00"));  // Goiás
            TABELA_FRETE.put(UF.MT, new BigDecimal("20.00"));  // Mato Grosso
            TABELA_FRETE.put(UF.MS, new BigDecimal("20.00"));  // Mato Grosso do Sul
    
            // ===== REGIÃO NORDESTE =====
            // Todos os 9 estados do Nordeste têm o mesmo valor de frete
            // Valor mais alto devido à maior distância logística
            TABELA_FRETE.put(UF.BA, new BigDecimal("25.00"));  // Bahia
            TABELA_FRETE.put(UF.SE, new BigDecimal("25.00"));  // Sergipe
            TABELA_FRETE.put(UF.AL, new BigDecimal("25.00"));  // Alagoas
            TABELA_FRETE.put(UF.PE, new BigDecimal("25.00"));  // Pernambuco
            TABELA_FRETE.put(UF.PB, new BigDecimal("25.00"));  // Paraíba
            TABELA_FRETE.put(UF.RN, new BigDecimal("25.00"));  // Rio Grande do Norte
            TABELA_FRETE.put(UF.CE, new BigDecimal("25.00"));  // Ceará
            TABELA_FRETE.put(UF.PI, new BigDecimal("25.00"));  // Piauí
            TABELA_FRETE.put(UF.MA, new BigDecimal("25.00"));  // Maranhão
    
            // ===== REGIÃO NORTE =====
            // Estados da região Norte - frete mais caro do país
            // Justificativa: menor infraestrutura logística, maiores distâncias,
            // dificuldades geográficas (floresta amazônica, rios, etc.)
            TABELA_FRETE.put(UF.AM, new BigDecimal("30.00"));  // Amazonas
            TABELA_FRETE.put(UF.AC, new BigDecimal("30.00"));  // Acre
            TABELA_FRETE.put(UF.RO, new BigDecimal("30.00"));  // Rondônia
            TABELA_FRETE.put(UF.RR, new BigDecimal("30.00"));  // Roraima
            TABELA_FRETE.put(UF.PA, new BigDecimal("30.00"));  // Pará
            TABELA_FRETE.put(UF.AP, new BigDecimal("30.00"));  // Amapá
            TABELA_FRETE.put(UF.TO, new BigDecimal("30.00"));  // Tocantins
        }
    
        /**
         * Calcula o valor do frete com base na Unidade Federativa (UF).
         *
         * O método consulta a tabela de fretes pré-definida e retorna o valor
         * correspondente ao estado. Caso a UF não seja encontrada na tabela
         * (fallback), retorna um valor padrão de R$ 25,00.
         *
         * Em um cenário real de produção, este método poderia:
         * - Integrar com API dos Correios (calcular frete por CEP)
         * - Considerar peso e dimensões dos produtos
         * - Oferecer múltiplas opções de frete (PAC, Sedex, etc.)
         * - Aplicar regras de frete grátis acima de determinado valor
         *
         * @param uf Unidade Federativa do endereço de entrega (ex: UF.SP, UF.RJ)
         * @return Valor do frete em reais (BigDecimal para precisão monetária)
         *
         * @example
         * FreteService service = new FreteService();
         * BigDecimal freteSP = service.calcularFrete(UF.SP);  // Retorna R$ 10.00
         * BigDecimal freteAM = service.calcularFrete(UF.AM);  // Retorna R$ 30.00
         * BigDecimal freteXX = service.calcularFrete(null);   // Retorna R$ 25.00 (fallback)
         */
        public BigDecimal calcularFrete(UF uf) {
            /**
             * getOrDefault(uf, default):
             * - Se a UF existir na tabela, retorna o valor associado
             * - Se não existir (UF == null ou UF não mapeada), retorna o valor padrão
             *
             * O valor padrão de R$ 25,00 foi escolhido por ser o valor do Nordeste,
             * que é uma média entre as regiões (nem o mais barato, nem o mais caro).
             */
            return TABELA_FRETE.getOrDefault(uf, new BigDecimal("25.00"));
        }
    }
=======
package br.com.java.e_commerce.nexus.service.carrinho;

import br.com.java.e_commerce.nexus.model.enums.UF;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Serviço responsável por calcular o valor do frete com base na Unidade Federativa (UF)
 * do endereço de entrega do cliente.
 *
 * Este serviço implementa uma tabela de fretes regionalizada, onde cada estado
 * (ou região) tem um valor de frete pré-definido. Em um sistema real, esta lógica
 * poderia ser expandida para integrar com APIs de transportadoras como Correios,
 * JadLog, Melhor Envio, etc.
 *
 * @Service: Marca a classe como um bean Spring gerenciado.
 *
 * NOTA: Esta classe NÃO tem a anotação @Transactional porque:
 * 1. Ela não realiza operações de banco de dados
 * 2. Apenas faz cálculos em memória usando uma tabela estática
 * 3. Transações seriam desnecessárias e adicionariam overhead desnecessário
 */
@Service
public class FreteService {

    /**
     * Tabela de fretes por Unidade Federativa (UF).
     *
     * Utilizamos EnumMap por ser altamente eficiente para chaves do tipo Enum.
     * O EnumMap é implementado internamente como um array, oferecendo performance O(1)
     * para operações de busca, inserção e remoção.
     *
     * Map<UF, BigDecimal> - Cada UF mapeia para um valor de frete em reais (R$)
     */
    private static final Map<UF, BigDecimal> TABELA_FRETE = new EnumMap<>(UF.class);

    /**
     * Bloco estático de inicialização da tabela de fretes.
     *
     * Este bloco é executado uma única vez quando a classe é carregada pela JVM,
     * populando o mapa com todos os valores de frete por estado/região.
     *
     * A tabela segue uma lógica regionalizada onde:
     * - Sudeste (especialmente SP) tem frete mais barato (centro de distribuição)
     * - Regiões mais distantes têm fretes progressivamente mais caros
     * - Norte tem o frete mais caro devido à logística mais complexa
     */
    static {
        // ===== REGIÃO SUDESTE =====
        // São Paulo - Estado com maior concentração de centros de distribuição
        // Frete mais barato devido à proximidade com os armazéns principais
        TABELA_FRETE.put(UF.SP, new BigDecimal("10.00"));

        // Demais estados do Sudeste
        // RJ, MG e ES têm frete ligeiramente mais caro que SP
        TABELA_FRETE.put(UF.RJ, new BigDecimal("15.00"));  // Rio de Janeiro
        TABELA_FRETE.put(UF.MG, new BigDecimal("15.00"));  // Minas Gerais
        TABELA_FRETE.put(UF.ES, new BigDecimal("15.00"));  // Espírito Santo

        // ===== REGIÃO SUL =====
        // PR, SC, RS - Frete intermediário
        // Região Sul tem boa infraestrutura logística, mas distante do eixo SP-RJ
        TABELA_FRETE.put(UF.PR, new BigDecimal("18.00"));  // Paraná
        TABELA_FRETE.put(UF.SC, new BigDecimal("18.00"));  // Santa Catarina
        TABELA_FRETE.put(UF.RS, new BigDecimal("18.00"));  // Rio Grande do Sul

        // ===== REGIÃO CENTRO-OESTE =====
        // DF, GO, MT, MS - Frete mais elevado
        // Região com menor densidade populacional e maior distância dos centros
        TABELA_FRETE.put(UF.DF, new BigDecimal("20.00"));  // Distrito Federal (Brasília)
        TABELA_FRETE.put(UF.GO, new BigDecimal("20.00"));  // Goiás
        TABELA_FRETE.put(UF.MT, new BigDecimal("20.00"));  // Mato Grosso
        TABELA_FRETE.put(UF.MS, new BigDecimal("20.00"));  // Mato Grosso do Sul

        // ===== REGIÃO NORDESTE =====
        // Todos os 9 estados do Nordeste têm o mesmo valor de frete
        // Valor mais alto devido à maior distância logística
        TABELA_FRETE.put(UF.BA, new BigDecimal("25.00"));  // Bahia
        TABELA_FRETE.put(UF.SE, new BigDecimal("25.00"));  // Sergipe
        TABELA_FRETE.put(UF.AL, new BigDecimal("25.00"));  // Alagoas
        TABELA_FRETE.put(UF.PE, new BigDecimal("25.00"));  // Pernambuco
        TABELA_FRETE.put(UF.PB, new BigDecimal("25.00"));  // Paraíba
        TABELA_FRETE.put(UF.RN, new BigDecimal("25.00"));  // Rio Grande do Norte
        TABELA_FRETE.put(UF.CE, new BigDecimal("25.00"));  // Ceará
        TABELA_FRETE.put(UF.PI, new BigDecimal("25.00"));  // Piauí
        TABELA_FRETE.put(UF.MA, new BigDecimal("25.00"));  // Maranhão

        // ===== REGIÃO NORTE =====
        // Estados da região Norte - frete mais caro do país
        // Justificativa: menor infraestrutura logística, maiores distâncias,
        // dificuldades geográficas (floresta amazônica, rios, etc.)
        TABELA_FRETE.put(UF.AM, new BigDecimal("30.00"));  // Amazonas
        TABELA_FRETE.put(UF.AC, new BigDecimal("30.00"));  // Acre
        TABELA_FRETE.put(UF.RO, new BigDecimal("30.00"));  // Rondônia
        TABELA_FRETE.put(UF.RR, new BigDecimal("30.00"));  // Roraima
        TABELA_FRETE.put(UF.PA, new BigDecimal("30.00"));  // Pará
        TABELA_FRETE.put(UF.AP, new BigDecimal("30.00"));  // Amapá
        TABELA_FRETE.put(UF.TO, new BigDecimal("30.00"));  // Tocantins
    }

    /**
     * Calcula o valor do frete com base na Unidade Federativa (UF).
     *
     * O método consulta a tabela de fretes pré-definida e retorna o valor
     * correspondente ao estado. Caso a UF não seja encontrada na tabela
     * (fallback), retorna um valor padrão de R$ 25,00.
     *
     * Em um cenário real de produção, este método poderia:
     * - Integrar com API dos Correios (calcular frete por CEP)
     * - Considerar peso e dimensões dos produtos
     * - Oferecer múltiplas opções de frete (PAC, Sedex, etc.)
     * - Aplicar regras de frete grátis acima de determinado valor
     *
     * @param uf Unidade Federativa do endereço de entrega (ex: UF.SP, UF.RJ)
     * @return Valor do frete em reais (BigDecimal para precisão monetária)
     *
     * @example
     * FreteService service = new FreteService();
     * BigDecimal freteSP = service.calcularFrete(UF.SP);  // Retorna R$ 10.00
     * BigDecimal freteAM = service.calcularFrete(UF.AM);  // Retorna R$ 30.00
     * BigDecimal freteXX = service.calcularFrete(null);   // Retorna R$ 25.00 (fallback)
     */
    public BigDecimal calcularFrete(UF uf) {
        /**
         * getOrDefault(uf, default):
         * - Se a UF existir na tabela, retorna o valor associado
         * - Se não existir (UF == null ou UF não mapeada), retorna o valor padrão
         *
         * O valor padrão de R$ 25,00 foi escolhido por ser o valor do Nordeste,
         * que é uma média entre as regiões (nem o mais barato, nem o mais caro).
         */
        return TABELA_FRETE.getOrDefault(uf, new BigDecimal("25.00"));
    }
}
>>>>>>> cc85b8d9e8047f09ba782373ee5397cd4b3cf4ab
