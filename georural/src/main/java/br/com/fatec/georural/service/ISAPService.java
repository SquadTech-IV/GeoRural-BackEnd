package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IsapDetalheAreaResponse;
import br.com.fatec.georural.dto.response.indicador.IsapResumoResponse;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.ResultadoIsapResumo;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.*;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ISAPService {

    private final ImovelRuralRepository imovelRuralRepository;
    private final IndicadorRepository indicadorRepository;
    private final RegraCalculoRepository regraCalculoRepository;
    private final ResultadoIsapResumoRepository resumoRepository;
    private final ResultadoIsapDetalheRepository detalheRepository;
    private final CalculoOracle calculoOracle;

    public record ResultadoLoteISAP(
            Long versaoId,
            Long camadaVersaoId,
            String escopo,
            long imoveisCalculados,
            long registrosPublicados
    ) {}

    @Transactional(readOnly = true)
    public IsapResumoResponse consultarPorImovelId(Long imovelId) {
        ResultadoIsapResumo resumo = resumoRepository.findVigentesPorImovelId(imovelId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum resultado vigente de ISAP encontrado para o imóvel " + imovelId));
        return montarComDetalhes(resumo);
    }

    @Transactional(readOnly = true)
    public IsapResumoResponse consultarPorCodigoCar(String codigoCar) {
        ResultadoIsapResumo resumo = resumoRepository.findVigentesPorCodigoCar(codigoCar)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum resultado vigente de ISAP encontrado para o CAR " + codigoCar));
        return montarComDetalhes(resumo);
    }

    @Transactional(readOnly = true)
    public List<IsapResumoResponse> listarHistoricoPorImovelId(Long imovelId) {
        List<ResultadoIsapResumo> historico = resumoRepository.findHistoricoPorImovelId(imovelId);
        if (historico.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Nenhum histórico de ISAP encontrado para o imóvel " + imovelId);
        }
        return historico.stream().map(this::montarComDetalhes).toList();
    }

    @Transactional(readOnly = true)
    public Page<IsapResumoResponse> listarPaginado(
            String situacao, Long municipioId, String busca, Pageable pageable) {
        String situacaoFiltro = (situacao != null && !situacao.isBlank()) ? situacao.trim() : null;
        String buscaFiltro = (busca != null && !busca.isBlank()) ? busca.trim() : null;

        return resumoRepository.consultarVigentesPaginado(situacaoFiltro, municipioId, buscaFiltro, pageable)
                .map(r -> IsapResumoResponse.de(r, List.of()));
    }

    @Transactional
    public IsapResumoResponse calcularPorImovel(Long imovelId, boolean publicar) {
        ImovelRural imovel = imovelRuralRepository.findById(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imóvel não encontrado: " + imovelId));

        if (imovel.getVersao() == null) {
            throw new EstadoInconsistenteException(
                    "Imóvel " + imovelId + " não possui versão vinculada para cálculo do ISAP.");
        }

        RegraCalculo regra = regraVigente();
        Long versaoId = imovel.getVersao().getId();

        long isrId = calculoOracle.calcularIsapImovel(imovelId, versaoId, null, null, regra.getId());
        if (isrId <= 0) {
            throw new EstadoInconsistenteException(
                    "Falha ao calcular ISAP no Oracle para o imóvel " + imovelId);
        }

        if (publicar) {
            calculoOracle.publicar(versaoId);
        }

        ResultadoIsapResumo resumo = resumoRepository.findById(isrId)
                .orElseThrow(() -> new EstadoInconsistenteException(
                        "Resumo ISAP recém-calculado não encontrado: " + isrId));
        return montarComDetalhes(resumo);
    }

    @Transactional
    public ResultadoLoteISAP calcularLote(
            Long versaoId, Long camadaVersaoId, String escopo, boolean publicar) {
        if (versaoId == null) {
            throw new EstadoInconsistenteException("versaoId é obrigatório para o cálculo em lote do ISAP.");
        }

        RegraCalculo regra = regraVigente();
        String escopoNorm = (escopo != null && !escopo.isBlank()) ? escopo.trim().toUpperCase() : "TODOS";

        long calculados = calculoOracle.calcularIsap(versaoId, null, regra.getId(), escopoNorm, camadaVersaoId);
        long publicados = (publicar && calculados > 0) ? calculoOracle.publicar(versaoId) : 0;

        return new ResultadoLoteISAP(versaoId, camadaVersaoId, escopoNorm, calculados, publicados);
    }

    private RegraCalculo regraVigente() {
        Indicador indicador = indicadorRepository.findBySigla("ISAP")
                .orElseThrow(() -> new EstadoInconsistenteException("Indicador ISAP não cadastrado."));
        return regraCalculoRepository.findByIndicadorAndVigente(indicador, "S")
                .orElseThrow(() -> new EstadoInconsistenteException("Nenhuma regra vigente para ISAP."));
    }

    private IsapResumoResponse montarComDetalhes(ResultadoIsapResumo resumo) {
        List<IsapDetalheAreaResponse> detalhes = detalheRepository.findPorResumoId(resumo.getId())
                .stream()
                .map(IsapDetalheAreaResponse::de)
                .toList();
        return IsapResumoResponse.de(resumo, detalhes);
    }
}