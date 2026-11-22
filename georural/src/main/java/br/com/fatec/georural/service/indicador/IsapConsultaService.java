package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.isap.IsapCategoriaResumoResponse;
import br.com.fatec.georural.dto.response.isap.IsapFeicaoDetalheResponse;
import br.com.fatec.georural.dto.response.isap.IsapImovelConsultaResponse;
import br.com.fatec.georural.dto.response.isap.IsapListaItemResponse;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.ResultadoIsapResumo;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIsapDetalheRepository;
import br.com.fatec.georural.repository.ResultadoIsapResumoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IsapConsultaService {

    private static final String AVISO_DUPLA_CONTAGEM =
            "As areas individuais das feicoes podem se sobrepor entre si e nao somam necessariamente o total da categoria ou o TOTAL do imovel.";

    private final ImovelRuralRepository imovelRepository;
    private final ResultadoIsapResumoRepository resumoRepository;
    private final ResultadoIsapDetalheRepository detalheRepository;

    @Transactional(readOnly = true)
    public PaginaResponse<IsapListaItemResponse> listar(
            String status,
            Long municipioId,
            String busca,
            Pageable pageable) {

        String statusNorm = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();
        String buscaNorm = (busca == null || busca.isBlank()) ? null : busca.trim();

        Page<ResultadoIsapResumo> page = resumoRepository.consultarVigentesPaginado(
                statusNorm, municipioId, buscaNorm, pageable);

        return PaginaResponse.de(page.map(IsapListaItemResponse::de));
    }

    @Transactional(readOnly = true)
    public IsapImovelConsultaResponse consultarPorImovelId(Long imovelId, Long versaoId) {
        ImovelRural imovel = imovelRepository.findById(imovelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imovel nao encontrado para o id: " + imovelId));

        return montarConsultaImovel(imovel, versaoId);
    }

    @Transactional(readOnly = true)
    public IsapImovelConsultaResponse consultarPorCodigoCar(String codigoCar, Long versaoId) {
        if (codigoCar == null || codigoCar.isBlank()) {
            throw new IllegalArgumentException("Codigo do CAR nao pode ser vazio.");
        }

        ImovelRural imovel = imovelRepository.findByCodigoCar(codigoCar.trim())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imovel nao encontrado para o CAR: " + codigoCar));

        return montarConsultaImovel(imovel, versaoId);
    }

    private IsapImovelConsultaResponse montarConsultaImovel(ImovelRural imovel, Long versaoId) {
        List<ResultadoIsapResumo> resumos = (versaoId != null)
                ? resumoRepository.findPorImovelIdEVersaoId(imovel.getId(), versaoId)
                : resumoRepository.findVigentesPorImovelId(imovel.getId());

        if (resumos.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Nenhum resultado ISAP encontrado para o imovel " + imovel.getCodigoCar() + ".");
        }

        ResultadoIsapResumo referencia = resumos.stream()
                .filter(r -> "TOTAL".equalsIgnoreCase(r.getCategoria()))
                .findFirst()
                .orElse(resumos.get(0));

        Long versaoUsadaId = referencia.getVersao().getId();

        List<IsapCategoriaResumoResponse> resumoDto = resumos.stream()
                .map(IsapCategoriaResumoResponse::de)
                .toList();

        List<IsapFeicaoDetalheResponse> detalhesDto = detalheRepository
                .findPorImovelEVersao(imovel.getId(), versaoUsadaId)
                .stream()
                .map(IsapFeicaoDetalheResponse::de)
                .toList();

        return new IsapImovelConsultaResponse(
                imovel.getId(),
                imovel.getCodigoCar(),
                imovel.getNome(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                referencia.getAreaImovelHa(),
                versaoUsadaId,
                referencia.getVersao().getNumero(),
                referencia.getRegraCalculo() != null ? referencia.getRegraCalculo().getVersao() : null,
                referencia.getDataCalculo(),
                AVISO_DUPLA_CONTAGEM,
                resumoDto,
                detalhesDto
        );
    }
}