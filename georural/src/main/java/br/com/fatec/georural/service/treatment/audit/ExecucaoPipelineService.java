package br.com.fatec.georural.service.treatment.audit;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExecucaoPipelineService {

    private final ExecucaoPipelineRepository repository;

    @Transactional
    public ExecucaoPipeline iniciar(
            ConjuntoDados conjunto,
            String etapa) {
        return iniciar(conjunto, null, etapa);
    }

    @Transactional
    public ExecucaoPipeline iniciar(
            ConjuntoDados conjunto,
            ArquivoBruto arquivoBruto,
            String etapa) {

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

    @Transactional
    public void finalizarComSucesso(
            ExecucaoPipeline execucao,
            long recebidos,
            long rejeitados) {

        if (execucao == null) {
            return;
        }

        long aprovados = Math.max(0, recebidos - rejeitados);
        LocalDateTime agora = LocalDateTime.now();

        execucao.setRegistrosRecebidos(recebidos);
        execucao.setRegistrosAprovados(aprovados);
        execucao.setRegistrosRejeitados(rejeitados);
        execucao.setRegistrosProcessados(recebidos);
        execucao.setStatus(rejeitados > 0 ? "COM_RESSALVA" : "CONCLUIDA");
        execucao.setFim(agora);

        if (execucao.getInicio() != null) {
            execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), agora).getSeconds());
        }

        repository.save(execucao);
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