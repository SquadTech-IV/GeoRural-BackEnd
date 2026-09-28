package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.mapper.IAEMapper;
import br.com.fatec.georural.exception.EstadoInconsistenteException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalculoIAEService {

    private final ImovelRuralRepository imovelRuralRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;
    private final IndicadorRepository indicadorRepository;
    private final RegraCalculoRepository regraCalculoRepository;
    private final IAEMapper mapper;

    public IAEResponse calcularSobreposicaoEmbargo(Long imovelId) {
        ImovelRural imovel = imovelRuralRepository.findById(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imóvel não encontrado: " + imovelId));

        Indicador indicador = indicadorRepository.findBySigla("IAE")
                .orElseThrow(() -> new EstadoInconsistenteException("Indicador IAE não cadastrado"));

        RegraCalculo regra = regraCalculoRepository.findByIndicadorAndVigente(indicador, "S")
                .orElseThrow(() -> new EstadoInconsistenteException("Nenhuma regra vigente para IAE"));

        Object[] resultadoAgregado = imovelRuralRepository.calcularAreaEmbargadaAgregada(imovelId);

        if (resultadoAgregado.length == 1 && resultadoAgregado[0] instanceof Object[] linha) {
            resultadoAgregado = linha;
        }

        BigDecimal areaEmbargada = toBigDecimal(resultadoAgregado[0]);
        BigDecimal areaTotal = toBigDecimal(resultadoAgregado[1]);

        BigDecimal percentual = areaTotal.compareTo(BigDecimal.ZERO) > 0
                ? areaEmbargada.divide(areaTotal, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;


        ResultadoIndicador entidade = resultadoIndicadorRepository
                .findByImovelIdAndIndicadorAndVersao(imovel.getId(), indicador, imovel.getVersao())
                .orElseGet(ResultadoIndicador::new);

        entidade.setImovel(imovel);
        entidade.setIndicador(indicador);
        entidade.setVersao(imovel.getVersao());
        entidade.setRegraCalculo(regra);
        entidade.setValorAbsoluto(areaEmbargada);
        entidade.setValorPercentual(percentual);
        entidade.setValorHectares(areaEmbargada.divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP));
        entidade.setDataCalculo(LocalDateTime.now());

        ResultadoIndicador salvo = resultadoIndicadorRepository.save(entidade);

        List<Object[]> embargosRaw = imovelRuralRepository.encontrarEmbargosIntersectantes(imovelId);
        return mapper.toResponse(salvo, embargosRaw);
    }

    private BigDecimal toBigDecimal(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        if (valor instanceof BigDecimal bd) {
            return bd;
        }
        if (valor instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }

        String texto = valor.toString().trim().replace(",", ".");
        if (texto.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new EstadoInconsistenteException(
                    "Valor numérico inválido: '" + valor + "' (tipo " + valor.getClass().getName() + ")");
        }
    }
}