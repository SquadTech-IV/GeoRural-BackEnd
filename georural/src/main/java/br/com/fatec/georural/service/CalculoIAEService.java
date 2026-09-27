package br.com.fatec.georural.service;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import br.com.fatec.georural.exception.EntidadeNaoEncontradaException;

@Service
@RequiredArgsConstructor
public class CalculoIAEService {

    private final ImovelRuralRepository imovelRuralRepository;
    private final IndicadorRepository indicadorRepository;
    private final RegraCalculoRepository regraCalculoRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;

    public ResultadoIndicador calcularSobreposicaoEmbargo(Long imovelId) {
        ImovelRural imovel = imovelRuralRepository.findById(imovelId)
            .orElseThrow(() -> new EntidadeNaoEncontradaException("Imóvel não encontrado: " + imovelId));

        Indicador indicador = indicadorRepository.findBySigla("IAE")
            .orElseThrow(() -> new IllegalStateException("Indicador IAE não cadastrado"));

        RegraCalculo regra = regraCalculoRepository.findByIndicadorAndVigente(indicador, "S")
            .orElseThrow(() -> new IllegalStateException("Nenhuma regra vigente para IAE"));

        Object[] resultado = imovelRuralRepository.calcularAreaEmbargadaAgregada(imovelId);

        BigDecimal areaEmbargada = resultado[0] != null
            ? new BigDecimal(resultado[0].toString())
            : BigDecimal.ZERO;
        BigDecimal areaTotal = new BigDecimal(resultado[1].toString());

        BigDecimal percentual = areaTotal.compareTo(BigDecimal.ZERO) > 0
            ? areaEmbargada.divide(areaTotal, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        ResultadoIndicador entidade = new ResultadoIndicador();
        //Sem valor absoluto
        entidade.setImovel(imovel);
        entidade.setIndicador(indicador);
        entidade.setVersao(imovel.getVersao());
        entidade.setRegraCalculo(regra);
        entidade.setValorPercentual(percentual);
        entidade.setValorHectares(areaEmbargada.divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP));
        entidade.setDataCalculo(LocalDateTime.now());

        return resultadoIndicadorRepository.save(entidade);
    }
}