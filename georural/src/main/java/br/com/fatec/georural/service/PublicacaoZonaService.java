package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.ObjetoJaExisteException;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublicacaoZonaService {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoZonaService.class);

    private final ResultadoIndicadorRepository resultadoRepository;
    private final ArmazenamentoZonaPort armazenamento;
    private final ObjectMapper mapper = new ObjectMapper();

    public PublicacaoZonaService(ResultadoIndicadorRepository resultadoRepository,
                                 ArmazenamentoZonaPort armazenamento) {
        this.resultadoRepository = resultadoRepository;
        this.armazenamento = armazenamento;
    }

    /** Grava manifest.json + resultados.json da versao na zona PUBLICADA (imutavel). */
    public void gravarArtefato(Versao versao) {
        Long conjuntoId = versao.getConjunto() != null ? versao.getConjunto().getId() : null;
        String base = "conjunto_" + conjuntoId + "/versao_" + versao.getNumero() + "/";

        List<ResultadoIndicador> resultados = resultadoRepository.buscarPorVersao(versao.getId());

        gravarJson(base + "manifest.json", montarManifest(versao, resultados.size()), versao.getHash());
        gravarJson(base + "resultados.json", montarResultados(resultados), versao.getHash());
    }

    private Map<String, Object> montarManifest(Versao v, int totalResultados) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versaoId", v.getId());
        m.put("numero", v.getNumero());
        m.put("hash", v.getHash());
        m.put("conjuntoId", v.getConjunto() != null ? v.getConjunto().getId() : null);
        m.put("conjuntoNome", v.getConjunto() != null ? v.getConjunto().getNome() : null);
        m.put("competencia", v.getCompetencia());
        m.put("execucaoId", v.getExecucao() != null ? v.getExecucao().getId() : null);
        m.put("publicadoEm", v.getDataPublicacao() != null
                ? v.getDataPublicacao().toString()
                : LocalDateTime.now().toString());
        m.put("publicadoPor", v.getUsuario() != null ? v.getUsuario().getNome() : null);
        m.put("totalResultados", totalResultados);
        m.put("situacao", v.getSituacao());
        return m;
    }

    private List<Map<String, Object>> montarResultados(List<ResultadoIndicador> resultados) {
        List<Map<String, Object>> lista = new ArrayList<>(resultados.size());
        for (ResultadoIndicador r : resultados) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("imovelId", r.getImovel() != null ? r.getImovel().getId() : null);
            item.put("codigoCar", r.getImovel() != null ? r.getImovel().getCodigoCar() : null);
            item.put("indicador", r.getIndicador() != null ? r.getIndicador().getSigla() : null);
            item.put("valorPercentual", r.getValorPercentual());
            item.put("valorHectares", r.getValorHectares());
            item.put("valorAbsoluto", r.getValorAbsoluto());
            lista.add(item);
        }
        return lista;
    }

    private void gravarJson(String caminho, Object conteudo, String hashVersao) {
        try {
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(conteudo);
            Map<String, String> metadados = new LinkedHashMap<>();
            metadados.put("Content-Type", "application/json");
            if (hashVersao != null) {
                metadados.put("x-versao-hash", hashVersao);
            }
            armazenamento.gravar(Zona.PUBLICADA, caminho,
                    new ByteArrayInputStream(bytes), bytes.length, metadados);
        } catch (ObjetoJaExisteException e) {
            // a zona publicada e imutavel: o objeto ja foi gravado numa publicacao anterior
            log.info("Artefato ja existe na zona PUBLICADA, mantido: {}", caminho);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao gravar artefato na zona PUBLICADA: " + caminho, e);
        }
    }
}
