package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IRLResponse;
import br.com.fatec.georural.dto.response.indicador.ResultadoIRL;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CalculoIRLGeoService {

    private static final BigDecimal M2_POR_HECTARE = BigDecimal.valueOf(10000);

    private final ImovelRuralRepository imovelRuralRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;
    private final IndicadorRepository indicadorRepository;
    private final RegraCalculoRepository regraCalculoRepository;
    private final CalculoIRLService calculoIRLService;              // US14 - a matematica
    private final ResultadoIndicadorService resultadoIndicadorService;

    public IRLResponse calcularReservaLegal(Long imovelId) {
        ImovelRural imovel = imovelRuralRepository.findById(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imóvel não encontrado: " + imovelId));

        Indicador indicador = indicadorRepository.findBySigla("IRL")
                .orElseThrow(() -> new EstadoInconsistenteException("Indicador IRL não cadastrado"));

        RegraCalculo regra = regraCalculoRepository.findByIndicadorAndVigente(indicador, "S")
                .orElseThrow(() -> new EstadoInconsistenteException("Nenhuma regra vigente para IRL"));

        Object[] agregado = imovelRuralRepository.calcularAreaReservaLegalAgregada(imovelId);
        if (agregado.length == 1 && agregado[0] instanceof Object[] linha) {
            agregado = linha;
        }

        BigDecimal areaReservaLegalM2 = toBigDecimal(agregado[0]);
        BigDecimal areaImovelHa = toBigDecimal(agregado[1]);   // imo_area_total ja esta em hectares
        BigDecimal areaReservaLegalHa = areaReservaLegalM2.divide(M2_POR_HECTARE, 4, RoundingMode.HALF_UP);

        ResultadoIRL irl = calculoIRLService.calcular(areaReservaLegalHa, areaImovelHa);

        String situacao = irl.saldoHa().compareTo(BigDecimal.ZERO) >= 0 ? "ok" : "ruim";

        ResultadoIndicador entidade = resultadoIndicadorRepository
                .findByImovelIdAndIndicadorAndVersao(imovel.getId(), indicador, imovel.getVersao())
                .orElseGet(ResultadoIndicador::new);

        entidade.setImovel(imovel);
        entidade.setIndicador(indicador);
        entidade.setVersao(imovel.getVersao());
        entidade.setRegraCalculo(regra);
        entidade.setValorPercentual(irl.percentualRL());      // % da area do imovel que e RL
        entidade.setValorHectares(irl.saldoHa());             // saldo (deficit negativo / excedente positivo)
        entidade.setValorAbsoluto(areaReservaLegalHa);        // area de RL em hectares
        entidade.setDataCalculo(LocalDateTime.now());
        entidade.setSituacao(situacao);
        entidade.setFeatures(toTexto(imovelRuralRepository.buscarReservaLegalGeoJson(imovelId)));

        ResultadoIndicador salvo = resultadoIndicadorService.salvarComoVigente(entidade);

        return new IRLResponse(
                salvo.getImovel().getId(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                imovel.getCodigoCar(),
                indicador.getSigla(),
                salvo.getValorPercentual(),
                salvo.getValorHectares(),
                salvo.getValorAbsoluto(),
                salvo.getDataCalculo(),
                salvo.getSituacao(),
                irl.minimoPercent(),
                irl.areaMinimaHa(),
                irl.versaoRegra());
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

    // Converte o retorno do SDO_UTIL.TO_GEOJSON  em texto
    private String toTexto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof String s) {
            return s;
        }
        if (valor instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (java.sql.SQLException e) {
                throw new EstadoInconsistenteException(
                        "Falha ao ler o GeoJSON da Reserva Legal: " + e.getMessage());
            }
        }
        return valor.toString();
    }
}