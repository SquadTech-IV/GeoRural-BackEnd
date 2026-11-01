package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaEtapaService {

    private final ExecucaoPipelineRepository execucaoRepository;
    private final ParametroExecucaoRepository parametroRepository;
    private final ConjuntoDadosRepository conjuntoRepository;
    private final ArquivoBrutoRepository arquivoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecucaoPipeline abrir(Etapa etapa, ContextoExecucao ctx) {
        ConjuntoDados conjunto = conjuntoRepository.findById(ctx.getConjuntoId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conjunto nao encontrado: " + ctx.getConjuntoId()));

        ExecucaoPipeline execucao = new ExecucaoPipeline();
        execucao.setConjunto(conjunto);
        execucao.setEtapa(etapa.name());
        execucao.setDagRunId(ctx.getDagRunId());
        execucao.setStatus("EM_ANDAMENTO");
        execucao.setInicio(LocalDateTime.now());
        execucao.setRegistrosRecebidos(0L);
        execucao.setRegistrosAprovados(0L);
        execucao.setRegistrosProcessados(0L);
        execucao.setRegistrosRejeitados(0L);

        if (ctx.getArquivoBrutoId() != null) {
            arquivoRepository.findById(ctx.getArquivoBrutoId()).ifPresent(execucao::setArquivoBruto);
        }
        if (ctx.getUsuarioId() != null) {
            usuarioRepository.findById(ctx.getUsuarioId()).ifPresent(execucao::setUsuario);
        }

        execucao = execucaoRepository.save(execucao);
        gravarParametros(execucao, ctx);

        log.info("[{}] etapa {} aberta (execucao id={})", ctx.getDagRunId(), etapa, execucao.getId());
        return execucao;
    }

    /** Os parametros da rodada ficam registrados para a versao poder ser reconstituida. */
    private void gravarParametros(ExecucaoPipeline execucao, ContextoExecucao ctx) {
        if (ctx.getCompetencia() != null) {
            ctx.getParametros().putIfAbsent("competencia", ctx.getCompetencia());
        }
        ctx.getParametros().forEach((nome, valor) -> {
            ParametroExecucao p = new ParametroExecucao();
            p.setExecucao(execucao);
            p.setNome(nome.length() > 60 ? nome.substring(0, 60) : nome);
            p.setValor(valor != null && valor.length() > 200 ? valor.substring(0, 200) : valor);
            parametroRepository.save(p);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecucaoPipeline fechar(Long execucaoId, ResultadoEtapa resultado) {
        ExecucaoPipeline execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new IllegalArgumentException("Execucao nao encontrada: " + execucaoId));

        if (resultado.recebidos() > 0
                && resultado.aprovados() + resultado.rejeitados() != resultado.recebidos()) {
            log.warn("Execucao id={}: recebidos ({}) != aprovados ({}) + rejeitados ({}).",
                    execucaoId, resultado.recebidos(), resultado.aprovados(), resultado.rejeitados());
        }

        LocalDateTime agora = LocalDateTime.now();
        execucao.setRegistrosRecebidos(resultado.recebidos());
        execucao.setRegistrosAprovados(resultado.aprovados());
        execucao.setRegistrosProcessados(resultado.recebidos());
        execucao.setRegistrosRejeitados(resultado.rejeitados());
        execucao.setMensagem(truncar(resultado.mensagem()));
        execucao.setStatus(resultado.comRessalva() ? "COM_RESSALVA" : "CONCLUIDA");
        execucao.setFim(agora);
        execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), agora).getSeconds());

        log.info("[{}] etapa {} fechada: {} (recebidos={}, aprovados={}, rejeitados={})",
                execucao.getDagRunId(), execucao.getEtapa(), execucao.getStatus(),
                resultado.recebidos(), resultado.aprovados(), resultado.rejeitados());

        return execucaoRepository.save(execucao);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(Long execucaoId, Throwable erro) {
        execucaoRepository.findById(execucaoId).ifPresent(execucao -> {
            LocalDateTime agora = LocalDateTime.now();
            execucao.setStatus("FALHOU");
            execucao.setMensagem(truncar(erro.getClass().getSimpleName() + ": " + erro.getMessage()));
            execucao.setFim(agora);
            if (execucao.getInicio() != null) {
                execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), agora).getSeconds());
            }
            execucaoRepository.save(execucao);
            log.error("[{}] etapa {} FALHOU: {}", execucao.getDagRunId(), execucao.getEtapa(),
                    erro.getMessage());
        });
    }

    private String truncar(String texto) {
        if (texto == null) return null;
        return texto.length() > 400 ? texto.substring(0, 397) + "..." : texto;
    }
}
