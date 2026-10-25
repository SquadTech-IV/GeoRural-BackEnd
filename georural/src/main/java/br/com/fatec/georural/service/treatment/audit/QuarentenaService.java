package br.com.fatec.georural.service.treatment.audit;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.geotools.api.feature.simple.SimpleFeature;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Quarentena;
import br.com.fatec.georural.entity.RegraQualidade;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.RegraQualidadeRepository;
import br.com.fatec.georural.repository.ValidacaoRepository;
import br.com.fatec.georural.service.treatment.common.FeatureMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuarentenaService {

    private final QuarentenaRepository quarentenaRepository;
    private final RegraQualidadeRepository regraRepository;
    private final ValidacaoRepository validacaoRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Cache das regras (antes era um findAll() a cada registro rejeitado). */
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

            // ASSUNÇÃO: a coluna linha_origem é numérica (Long). Se for outro tipo, ajuste.
            quarentena.setLinhaOrigem(linhaOrigem(feature));

            quarentena.setDadoOriginal(
                    mapper.writeValueAsString(
                            FeatureMapper.paraMapa(feature)));

            quarentenaRepository.save(quarentena);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao registrar quarentena",
                    e);
        }
    }

    /**
     * VALIDACAO.arquivoBruto é obrigatório (not-null), então a validação
     * precisa nascer já ligada ao arquivo bruto da execução.
     */
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

        // provisório: o valor final é definido em encerrarValidacao
        validacao.setStatusFinal("APROVADO");

        return validacaoRepository.save(validacao);
    }

    /**
     * Fecha a validação com os totais reais. Unidade: registros (feições) do
     * arquivo de origem. CK_VAL_STATUS só aceita APROVADO / REJEITADO.
     */
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
                            novo.putIfAbsent(
                                    r.getTipo().toUpperCase(),
                                    r);
                        }
                    }

                    regrasPorTipo = novo;
                }

                mapa = regrasPorTipo;
            }
        }

        return mapa.get(tipo.toUpperCase());
    }

    /** O ID de feição de shapefile tem o formato "nome.123"; usa o 123. */
    private Long linhaOrigem(SimpleFeature feature) {

        String id = feature.getID();

        if (id == null) {
            return null;
        }

        try {
            return Long.parseLong(
                    id.substring(id.lastIndexOf('.') + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
