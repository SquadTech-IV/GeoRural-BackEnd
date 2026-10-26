package br.com.fatec.georural.service.treatment.audit;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.api.feature.simple.SimpleFeature;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fatec.georural.dto.ingestao.ArquivoQuarentenaResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Quarentena;
import br.com.fatec.georural.entity.RegraQualidade;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.RegraQualidadeRepository;
import br.com.fatec.georural.repository.ValidacaoRepository;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import br.com.fatec.georural.service.treatment.common.FeatureMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuarentenaService {

    private final QuarentenaRepository quarentenaRepository;
    private final RegraQualidadeRepository regraRepository;
    private final ValidacaoRepository validacaoRepository;
    private final ArmazenamentoZonaPort armazenamentoZonaPort;

    private final ObjectMapper mapper = new ObjectMapper();

    private volatile Map<String, RegraQualidade> regrasPorTipo;

    public void registrar(
            Validacao validacao,
            String tipoRegra,
            String coluna,
            String motivo,
            SimpleFeature feature) {

        try {
            Quarentena quarentena = new Quarentena();
            quarentena.setValidacao(validacao);
            quarentena.setRegra(regra(tipoRegra));
            quarentena.setColunaErro(coluna);
            quarentena.setMotivoErro(motivo);
            quarentena.setSituacao("PENDENTE");
            quarentena.setDataRegistro(LocalDateTime.now());
            quarentena.setLinhaOrigem(linhaOrigem(feature));

            quarentena.setDadoOriginal(
                    mapper.writeValueAsString(
                            FeatureMapper.paraMapa(feature)));

            quarentenaRepository.save(quarentena);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao registrar quarentena", e);
        }
    }

    public Validacao iniciarValidacao(ArquivoBruto arquivoBruto) {
        return iniciarValidacao(arquivoBruto, null);
    }

    public Validacao iniciarValidacao(
            ArquivoBruto arquivoBruto,
            ExecucaoPipeline execucao) {

        Validacao validacao = new Validacao();
        validacao.setArquivoBruto(arquivoBruto);
        validacao.setExecucao(execucao);
        validacao.setDataExecucao(LocalDateTime.now());
        validacao.setStatusFinal("APROVADO");

        return validacaoRepository.save(validacao);
    }

    public void encerrarValidacao(
            Validacao validacao,
            long total,
            long invalidos) {

        if (validacao == null) {
            return;
        }

        long validos = Math.max(0, total - invalidos);

        validacao.setTotalRegistros(total);
        validacao.setRegistrosValidos(validos);
        validacao.setRegistrosInvalidos(invalidos);
        validacao.setStatusFinal(invalidos > 0 ? "REJEITADO" : "APROVADO");

        validacaoRepository.save(validacao);

        if (invalidos > 0) {
            salvarQuarentenaNaZona(validacao);
        }
    }

    public void salvarQuarentenaNaZona(Validacao validacao) {
        List<Quarentena> rejeitados = quarentenaRepository.findByValidacao_Id(validacao.getId());
        if (rejeitados.isEmpty()) {
            return;
        }

        ExecucaoPipeline exec = validacao.getExecucao();
        ArquivoBruto arq = validacao.getArquivoBruto();

        Long execId = exec != null ? exec.getId() : validacao.getId();
        Long arqId = arq != null ? arq.getId() : null;
        String arqNome = arq != null ? arq.getNome() : "desconhecido";

        List<ArquivoQuarentenaResponse.ItemRejeitadoJson> itens = rejeitados.stream()
                .map(q -> ArquivoQuarentenaResponse.ItemRejeitadoJson.builder()
                        .quarentenaId(q.getId())
                        .linhaOrigem(q.getLinhaOrigem())
                        .colunaErro(q.getColunaErro())
                        .codigoRegra(q.getRegra() != null ? q.getRegra().getTipo() : null)
                        .nomeRegra(q.getRegra() != null ? q.getRegra().getNome() : null)
                        .motivoErro(q.getMotivoErro())
                        .dadoOriginal(q.getDadoOriginal())
                        .build())
                .toList();

        ArquivoQuarentenaResponse jsonDto = ArquivoQuarentenaResponse.builder()
                .execucaoId(execId)
                .validacaoId(validacao.getId())
                .arquivoBrutoId(arqId)
                .nomeArquivoBruto(arqNome)
                .dataGeracao(LocalDateTime.now())
                .totalRegistrosRejeitados((long) itens.size())
                .registros(itens)
                .build();

        try {
            String jsonConteudo = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonDto);
            String caminhoRelativo = String.format("execucao_%d/quarentena_exec_%d.json", execId, execId);

            byte[] bytes = jsonConteudo.getBytes(StandardCharsets.UTF_8);
            Map<String, String> metadados = Map.of("Content-Type", "application/json");

            armazenamentoZonaPort.gravar(
                    Zona.QUARENTENA,
                    caminhoRelativo,
                    new ByteArrayInputStream(bytes),
                    bytes.length,
                    metadados
            );
        } catch (Exception e) {
            throw new RuntimeException("Falha ao gravar arquivo JSON na zona de quarentena", e);
        }
    }

    private RegraQualidade regra(String tipo) {
        if (tipo == null) {
            return null;
        }

        Map<String, RegraQualidade> mapa = regrasPorTipo;
        if (mapa == null) {
            synchronized (this) {
                if (regrasPorTipo == null) {
                    Map<String, RegraQualidade> novo = new HashMap<>();
                    for (RegraQualidade r : regraRepository.findAll()) {
                        if (r.getTipo() != null) {
                            novo.putIfAbsent(r.getTipo().toUpperCase(), r);
                        }
                    }
                    regrasPorTipo = novo;
                }
                mapa = regrasPorTipo;
            }
        }
        return mapa.get(tipo.toUpperCase());
    }

    private Long linhaOrigem(SimpleFeature feature) {
        String id = feature.getID();
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(id.substring(id.lastIndexOf('.') + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}