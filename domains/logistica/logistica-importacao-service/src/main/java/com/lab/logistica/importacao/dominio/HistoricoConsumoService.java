package com.lab.logistica.importacao.dominio;

import com.lab.logistica.importacao.api.dto.ConsumoMedioResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Camada de "cálculo otimizado" pedida sobre a base de entregas: quando há histórico real
 * suficiente para o modelo, usa a MÉDIA do consumo medido (mais fiel que a tabela
 * nominal); sem histórico, cai para a tabela nominal (mesmos valores de TipoCaminhao em
 * logistica-rota-service), documentado no campo "fonte" da resposta em vez de escondido.
 *
 * <p>Bucket simples por status vazio/carregado (carga == 0 vs. carga &gt; 0) -- é o mesmo
 * nível de granularidade que o app já usa (consumoVazio/consumoCarregado). Uma versão
 * futura pode interpolar por faixa de carga em vez de só dois buckets, quando houver
 * volume de dados que justifique.
 */
@Service
public class HistoricoConsumoService {

    private final EntregaHistoricoRepository repository;

    public HistoricoConsumoService(EntregaHistoricoRepository repository) {
        this.repository = repository;
    }

    public ConsumoMedioResponse consumoMedio(String caminhaoBruto, double cargaToneladas) {
        String caminhao = caminhaoBruto.trim().toUpperCase().replace(' ', '_');
        if (!ModelosCaminhaoConhecidos.ehValido(caminhao)) {
            throw new IllegalArgumentException("caminhao desconhecido: \"" + caminhaoBruto
                    + "\" (esperado um de " + ModelosCaminhaoConhecidos.NOMES_VALIDOS + ")");
        }

        boolean carregado = cargaToneladas > 0;
        List<Double> amostras = repository.findByCaminhaoAndConsumoKmLIsNotNull(caminhao).stream()
                .filter(e -> (e.getCargaToneladas() > 0) == carregado)
                .map(EntregaHistorico::getConsumoKmL)
                .toList();

        if (amostras.isEmpty()) {
            double nominal = ModelosCaminhaoConhecidos.consumoNominalKmL(caminhao, cargaToneladas);
            return new ConsumoMedioResponse(caminhao, cargaToneladas, nominal, 0, "tabela_nominal");
        }

        double media = amostras.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return new ConsumoMedioResponse(caminhao, cargaToneladas, media, amostras.size(), "historico");
    }
}
