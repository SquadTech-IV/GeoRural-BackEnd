package br.com.fatec.georural.service.qualidade;

import br.com.fatec.georural.dto.response.qualidade.ExecucaoConsultaResponse;
import br.com.fatec.georural.dto.response.qualidade.QuarentenaConsultaResponse;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Quarentena;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.QuarentenaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultaExecucaoQuarentenaService {

    private final ExecucaoPipelineRepository execucaoRepository;
    private final QuarentenaRepository quarentenaRepository;

    @Transactional(readOnly = true)
    public Page<ExecucaoConsultaResponse> listarExecucoes(Long conjuntoId, Pageable pageable) {
        Page<ExecucaoPipeline> page = execucaoRepository.listarPorConjuntoPaginado(conjuntoId, pageable);
        return page.map(e -> ExecucaoConsultaResponse.builder()
                .id(e.getId())
                .conjuntoId(e.getConjunto() != null ? e.getConjunto().getId() : null)
                .nomeConjunto(e.getConjunto() != null ? e.getConjunto().getNome() : null)
                .arquivoBrutoId(e.getArquivoBruto() != null ? e.getArquivoBruto().getId() : null)
                .nomeArquivoBruto(e.getArquivoBruto() != null ? e.getArquivoBruto().getNome() : null)
                .etapa(e.getEtapa())
                .status(e.getStatus())
                .registrosRecebidos(e.getRegistrosRecebidos())
                .registrosAprovados(e.getRegistrosAprovados())
                .registrosRejeitados(e.getRegistrosRejeitados())
                .inicio(e.getInicio())
                .fim(e.getFim())
                .duracaoSeg(e.getDuracaoSeg())
                .build());
    }

    @Transactional(readOnly = true)
    public Page<QuarentenaConsultaResponse> consultarQuarentena(
            Long conjuntoId,
            Long execucaoId,
            String situacao,
            Pageable pageable) {

        Page<Quarentena> page = quarentenaRepository.consultarQuarentenaPaginada(
                conjuntoId, execucaoId, situacao, pageable
        );

        return page.map(q -> {
            var val = q.getValidacao();
            var exec = val != null ? val.getExecucao() : null;
            var arq = val != null ? val.getArquivoBruto() : null;
            var conj = arq != null ? arq.getConjunto() : (exec != null ? exec.getConjunto() : null);

            return QuarentenaConsultaResponse.builder()
                    .quarentenaId(q.getId())
                    .execucaoId(exec != null ? exec.getId() : null)
                    .conjuntoId(conj != null ? conj.getId() : null)
                    .nomeConjunto(conj != null ? conj.getNome() : null)
                    .arquivoBrutoId(arq != null ? arq.getId() : null)
                    .nomeArquivoBruto(arq != null ? arq.getNome() : null)
                    .linhaOrigem(q.getLinhaOrigem())
                    .colunaErro(q.getColunaErro())
                    .codigoRegra(q.getRegra() != null ? q.getRegra().getTipo() : null)
                    .nomeRegra(q.getRegra() != null ? q.getRegra().getNome() : null)
                    .motivoErro(q.getMotivoErro())
                    .dadoOriginal(q.getDadoOriginal())
                    .situacao(q.getSituacao())
                    .dataRegistro(q.getDataRegistro())
                    .build();
        });
    }
}