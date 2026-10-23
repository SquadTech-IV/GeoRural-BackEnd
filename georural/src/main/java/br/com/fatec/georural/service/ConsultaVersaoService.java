package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.versao.IndicadorResumoResponse;
import br.com.fatec.georural.dto.response.versao.VersaoResumoResponse;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsultaVersaoService {

    private static final String SITUACAO_VIGENTE = "VIGENTE";

    private final VersaoRepository versaoRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;

    @Transactional(readOnly = true)
    public List<VersaoResumoResponse> listar(String situacao) {
        List<Versao> versoes = (situacao == null || situacao.isBlank())
                ? versaoRepository.listarResumo()
                : versaoRepository.listarResumoPorSituacao(situacao.trim().toUpperCase());

        if (versoes.isEmpty()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>(versoes.size());
        for (Versao v : versoes) {
            ids.add(v.getId());
        }

        // indicadores e regras de calculo por versao
        Map<Long, LinkedHashSet<IndicadorResumoResponse>> indicadoresPorVersao = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<String>> regrasPorVersao = new LinkedHashMap<>();
        for (Object[] linha : resultadoIndicadorRepository.listarIndicadoresPorVersoes(ids)) {
            Long versaoId = ((Number) linha[0]).longValue();
            String sigla = (String) linha[1];
            String nome = (String) linha[2];
            String regraCalculo = (String) linha[3];
            indicadoresPorVersao
                    .computeIfAbsent(versaoId, k -> new LinkedHashSet<>())
                    .add(new IndicadorResumoResponse(sigla, nome));
            if (regraCalculo != null) {
                regrasPorVersao
                        .computeIfAbsent(versaoId, k -> new LinkedHashSet<>())
                        .add(regraCalculo);
            }
        }

        // versao vigente atual por conjunto
        Map<Long, Long> vigenteNumeroPorConjunto = new LinkedHashMap<>();
        Map<Long, LocalDateTime> vigenteDesdePorConjunto = new LinkedHashMap<>();
        for (Versao vigente : versaoRepository.listarResumoPorSituacao(SITUACAO_VIGENTE)) {
            Long conjuntoId = vigente.getConjunto().getId();
            vigenteNumeroPorConjunto.putIfAbsent(conjuntoId, vigente.getNumero());
            vigenteDesdePorConjunto.putIfAbsent(conjuntoId, vigente.getDataPublicacao());
        }

        List<VersaoResumoResponse> resumo = new ArrayList<>(versoes.size());
        for (Versao v : versoes) {
            Long conjuntoId = v.getConjunto().getId();
            resumo.add(new VersaoResumoResponse(
                    v.getId(),
                    v.getNumero(),
                    v.getSituacao(),
                    conjuntoId,
                    v.getConjunto().getNome(),
                    new ArrayList<>(indicadoresPorVersao.getOrDefault(v.getId(), new LinkedHashSet<>())),
                    new ArrayList<>(regrasPorVersao.getOrDefault(v.getId(), new LinkedHashSet<>())),
                    v.getCompetencia(),
                    v.getExecucao() != null ? v.getExecucao().getId() : null,
                    v.getDataPublicacao(),
                    v.getUsuario() != null ? v.getUsuario().getNome() : null,
                    vigenteNumeroPorConjunto.get(conjuntoId),
                    vigenteDesdePorConjunto.get(conjuntoId)));
        }
        return resumo;
    }
}