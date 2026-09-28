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
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.mapper.IAEMapper;
import br.com.fatec.georural.exception.EstadoInconsistenteException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalculoIAEService {

    private final ImovelRuralRepository imovelRuralRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;
    private final IndicadorRepository indicadorRepository;
    private final RegraCalculoRepository regraCalculoRepository;
    private final IAEMapper mapper;
    private final VersaoEmbargoService versaoEmbargoResolver;

    public IAEResponse calcularSobreposicaoEmbargo(Long imovelId) {
        ImovelRural imovel = imovelRuralRepository.findById(imovelId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado: " + imovelId));

        Indicador indicador = indicadorRepository.findBySigla("IAE")
                .orElseThrow(() -> new EstadoInconsistenteException("Indicador IAE não cadastrado"));

        RegraCalculo regra = regraCalculoRepository.findByIndicadorAndVigente(indicador, "S")
                .orElseThrow(() -> new EstadoInconsistenteException("Nenhuma regra vigente para IAE"));

        if (resultadoIndicadorRepository.existsByImovelIdAndIndicadorAndVersao(
                imovelId, indicador, imovel.getVersao())) {
            throw new ConflitoException("IAE já calculado para o imóvel " + imovelId);
        }

        Long versaoEmbargoId = versaoEmbargoResolver.resolverId();
        Object[] agregado = imovelRuralRepository.calcularAreaEmbargadaAgregada(imovelId, versaoEmbargoId);

        BigDecimal areaEmbargadaM2 = agregado[0] != null
            ? new BigDecimal(agregado[0].toString())
            : BigDecimal.ZERO;
        BigDecimal areaTotalHa = new BigDecimal(agregado[1].toString());

        BigDecimal areaEmbargadaHa = areaEmbargadaM2.divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
        BigDecimal percentual = areaTotalHa.compareTo(BigDecimal.ZERO) > 0
            ? areaEmbargadaM2
                .divide(areaTotalHa.multiply(BigDecimal.valueOf(10000)), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        ResultadoIndicador entidade = new ResultadoIndicador();
        entidade.setImovel(imovel);
        entidade.setIndicador(indicador);
        entidade.setVersao(imovel.getVersao());
        entidade.setRegraCalculo(regra);
        entidade.setValorAbsoluto(areaEmbargadaM2);
        entidade.setValorPercentual(percentual);
        entidade.setValorHectares(areaEmbargadaHa);
        entidade.setDataCalculo(LocalDateTime.now());

        ResultadoIndicador salvo;
        try {
            salvo = resultadoIndicadorRepository.save(entidade);
        } catch (DataIntegrityViolationException e) {
            // Corrida entre duas chamadas: a constraint única pega o que o exists não pegou.
            if (e.getMessage() != null && e.getMessage().toUpperCase().contains("UK_RIN_IMO_IND_VER")) {
                throw new ConflitoException("IAE já calculado para o imóvel " + imovelId);
            }
            throw e;
        }

        List<Object[]> embargosRaw = imovelRuralRepository.encontrarEmbargosIntersectantes(imovelId, versaoEmbargoId);
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