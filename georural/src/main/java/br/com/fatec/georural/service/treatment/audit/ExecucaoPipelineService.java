package br.com.fatec.georural.service.treatment.audit;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo ciclo de vida e fechamento de execuções do pipeline (US#08 - T11).
 * Garante a verificação estrita do invariante: recebidos = aprovados + rejeitados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecucaoPipelineService {

    private final ExecucaoPipelineRepository repository;

    @Transactional
    public ExecucaoPipeline iniciar(ConjuntoDados conjunto, String etapa) {
        return iniciar(conjunto, null, etapa);
    }

    @Transactional
    public ExecucaoPipeline iniciar(ConjuntoDados conjunto, ArquivoBruto arquivoBruto, String etapa) {
        ExecucaoPipeline execucao = new ExecucaoPipeline();
        execucao.setConjunto(conjunto);
        execucao.setArquivoBruto(arquivoBruto);
        execucao.setEtapa(etapa);
        execucao.setStatus("EM_ANDAMENTO");
        execucao.setInicio(LocalDateTime.now());
        execucao.setRegistrosRecebidos(0L);
        execucao.setRegistrosAprovados(0L);
        execucao.setRegistrosRejeitados(0L);
        execucao.setRegistrosProcessados(0L);

        return repository.save(execucao);
    }

    /**
     * Realiza o fechamento da execução validando a invariante:
     * recebidos = aprovados + rejeitados (calculado por registros/feições, não por motivos).
     */
    @Transactional
    public ExecucaoPipeline finalizarComSucesso(ExecucaoPipeline execucao, long recebidos, long rejeitados) {
        if (execucao == null) {
            throw new IllegalArgumentException("Execução do pipeline não pode ser nula.");
        }

        long aprovados = recebidos - rejeitados;

        // Validação estrita da invariante (US#08 - T11 / DoD)
        if (recebidos < 0 || rejeitados < 0 || aprovados < 0 || (aprovados + rejeitados != recebidos)) {
            String mensagemErro = String.format(
                    "Invariante de execução violado para a execução id=%d: recebidos (%d) != aprovados (%d) + rejeitados (%d)",
                    execucao.getId(), recebidos, aprovados, rejeitados
            );
            log.error(mensagemErro);
            throw new EstadoInconsistenteException(mensagemErro);
        }

        LocalDateTime agora = LocalDateTime.now();

        execucao.setRegistrosRecebidos(recebidos);
        execucao.setRegistrosAprovados(aprovados);
        execucao.setRegistrosRejeitados(rejeitados);
        execucao.setRegistrosProcessados(recebidos);

        // Situação final
        execucao.setStatus(rejeitados > 0 ? "COM_RESSALVA" : "CONCLUIDA");
        execucao.setFim(agora);

        if (execucao.getInicio() != null) {
            execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), agora).getSeconds());
        }

        log.info("Fechamento da execução id={} realizado com sucesso. Status: {}, Recebidos: {}, Aprovados: {}, Rejeitados: {}",
                execucao.getId(), execucao.getStatus(), recebidos, aprovados, rejeitados);

        return repository.save(execucao);
    }

    @Transactional
    public void registrarFalhaTecnica(ExecucaoPipeline execucao, Throwable e) {
        if (execucao == null) {
            return;
        }

        LocalDateTime agora = LocalDateTime.now();
        execucao.setStatus("FALHOU");
        execucao.setFim(agora);

        if (execucao.getInicio() != null) {
            execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), agora).getSeconds());
        }

        repository.save(execucao);
    }
}