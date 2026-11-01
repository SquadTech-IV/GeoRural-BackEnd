package br.com.fatec.georural.service.pipeline.calculo;

import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.repository.ParametroRegraCalculoRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

// resolve qual regra de calculo o pipeline vai usar
@Slf4j
@Service
@RequiredArgsConstructor
public class RegraVigenteService {

    private final RegraCalculoRepository regraRepository;
    private final ParametroRegraCalculoRepository parametroRepository;

    @Transactional(readOnly = true)
    public RegraEmUso emUso(String sigla, Long regraIdForcada) {
        RegraCalculo regra = regraIdForcada != null
                ? regraRepository.findById(regraIdForcada)
                        .orElseThrow(() -> new RegraNaoEncontradaException(
                                "Regra de calculo " + regraIdForcada + " nao encontrada."))
                : regraRepository.findFirstByIndicador_SiglaAndVigenteOrderByDataCriacaoDesc(sigla, "S")
                        .orElseThrow(() -> new RegraNaoEncontradaException(
                                "Nenhuma regra de calculo vigente para o indicador " + sigla + "."));

        Map<String, String> parametros = new LinkedHashMap<>();
        for (ParametroRegraCalculo p : parametroRepository
                .findByRegraCalculo_IdOrderByNomeAsc(regra.getId())) {
            parametros.put(p.getNome(), p.getValor());
        }
        return new RegraEmUso(regra.getId(), regra.getVersao(), regra.getFormula(), parametros);
    }

    public record RegraEmUso(Long id, String versao, String formula, Map<String, String> parametros) {

        public double numero(String nome, double padrao) {
            String valor = parametros.get(nome);
            if (valor == null || valor.isBlank()) {
                return padrao;
            }
            try {
                return Double.parseDouble(valor.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return padrao;
            }
        }
    }

    public static class RegraNaoEncontradaException extends RuntimeException {
        public RegraNaoEncontradaException(String mensagem) {
            super(mensagem);
        }
    }

}
