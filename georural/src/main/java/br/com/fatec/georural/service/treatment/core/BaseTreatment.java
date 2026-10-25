package br.com.fatec.georural.service.treatment.core;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.service.treatment.audit.QuarentenaService;
import br.com.fatec.georural.service.treatment.audit.TipoTransformacao;
import br.com.fatec.georural.service.treatment.audit.TransformacaoService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseTreatment {

    private TreatmentState state;

    private List<SimpleFeature> features;

    private ArquivoBruto arquivoBruto;

    private Versao versao;

    private ExecucaoPipeline execucaoPipeline;

    private File file;

    private CoordinateReferenceSystem targetCRS;

    /**
     * Validação da execução: é a ela que os registros da QUARENTENA ficam ligados.
     */
    private Validacao validacao;

    /** Total de registros enviados para a QUARENTENA nesta execução. */
    @Setter(AccessLevel.NONE)
    private int totalRejeitados = 0;

    /** Total de registros lidos do arquivo (base do VALIDACAO.val_total_registros). */
    private int totalCarregado = 0;

    public void execute() {

        try {

            while (state != null) {

                TreatmentState atual = state;

                atual.execute(this);

                if (state == atual) {
                    state = proximoApos(atual);
                }
            }

        } catch (Exception e) {

            if (execucaoPipeline != null) {

                execucaoPipeline.setStatus("FALHOU");

                execucaoPipeline.setFim(LocalDateTime.now());

                execucaoPipeline.setDuracaoSeg(
                        Duration.between(
                                execucaoPipeline.getInicio(),
                                LocalDateTime.now())
                                .getSeconds());

                SpringContextHolder
                        .getBean(ExecucaoPipelineRepository.class)
                        .save(execucaoPipeline);
            }

            throw e;
        }
    }

    /**
     * Registra uma transformação na tabela TRANSFORMACAO, ligada à execução
     * corrente. A ordem é sequencial e automática (1, 2, 3...), então os
     * estados não precisam mais "adivinhar" um número fixo.
     */
    public void registrarTransformacao(
            String tipo,
            int entrada,
            int saida,
            String detalhe) {

        if (execucaoPipeline == null) {
            return;
        }

        String descricao = "[" + tipo + "] entrada=" + entrada
                + ", saída=" + saida
                + (detalhe != null && !detalhe.isBlank()
                        ? ", " + detalhe
                        : "");

        SpringContextHolder
                .getBean(TransformacaoService.class)
                .registrar(
                        execucaoPipeline,
                        0,
                        TipoTransformacao.paraBanco(tipo),
                        descricao);
    }

    /**
     * Envia a feição reprovada para a QUARENTENA, com o motivo, e conta como
     * rejeitada. Depois de chamar este método o estado deve dar "continue" e
     * NÃO incluir a feição na lista de aprovadas.
     */
    public void rejeitar(
            String tipoRegra,
            String coluna,
            String motivo,
            SimpleFeature feature) {

        QuarentenaService quarentena = SpringContextHolder
                .getBean(QuarentenaService.class);

        if (validacao == null) {
            validacao = quarentena.iniciarValidacao(arquivoBruto, execucaoPipeline);
        }

        quarentena.registrar(
                validacao,
                tipoRegra,
                coluna,
                motivo,
                feature);

        totalRejeitados++;
    }

    /** Fecha a VALIDACAO desta execução com os totais e o status final. */
    public void encerrarValidacao() {

        SpringContextHolder
                .getBean(QuarentenaService.class)
                .encerrarValidacao(validacao, totalCarregado, totalRejeitados);
    }

    protected abstract TreatmentState proximoApos(
            TreatmentState atual);
}
