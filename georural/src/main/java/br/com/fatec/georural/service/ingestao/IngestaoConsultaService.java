package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConjuntoIngestaoResponse;
import br.com.fatec.georural.dto.ingestao.FonteIngestaoResponse;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class IngestaoConsultaService {

    private final ConjuntoDadosRepository conjuntoRepository;

    @Transactional(readOnly = true)
    public List<FonteIngestaoResponse> listarFontesComApi() {
        List<ConjuntoDados> conjuntos = conjuntoRepository.buscarConjuntosApi();

        Map<Long, FonteDoDado> fontes = new LinkedHashMap<>();
        Map<Long, List<ConjuntoIngestaoResponse>> porFonte = new LinkedHashMap<>();

        for (ConjuntoDados c : conjuntos) {
            FonteDoDado f = c.getFonte();
            fontes.putIfAbsent(f.getId(), f);
            porFonte.computeIfAbsent(f.getId(), k -> new ArrayList<>()).add(toConjunto(c));
        }

        List<FonteIngestaoResponse> resposta = new ArrayList<>();
        for (Map.Entry<Long, FonteDoDado> e : fontes.entrySet()) {
            FonteDoDado f = e.getValue();
            resposta.add(new FonteIngestaoResponse(f.getId(), f.getNome(), f.getSigla(), porFonte.get(e.getKey())));
        }
        return resposta;
    }

    private ConjuntoIngestaoResponse toConjunto(ConjuntoDados c) {
        String forma = c.getFormaEntrada();
        boolean aceitaApi = "API".equals(forma) || "AMBOS".equals(forma);
        String tipoNome = (c.getTipo() != null) ? c.getTipo().getNome() : null;
        return new ConjuntoIngestaoResponse(
                c.getId(), c.getNome(), tipoNome, forma,
                aceitaApi, c.getApiEndpoint(), c.getApiFormato(), c.getPeriodicidade());
    }
}