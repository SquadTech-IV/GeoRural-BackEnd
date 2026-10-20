package br.com.fatec.georural.service.qualidade;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.RegraVioladaResponse;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.ValidacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


@Service
@RequiredArgsConstructor
public class QualidadeService {

    private static final String SITUACAO_PENDENTE = "PENDENTE";
    private static final String STATUS_APROVADO = "APROVADO";
    private static final String STATUS_REJEITADO = "REJEITADO";
    private static final String STATUS_SEM_DADOS = "SEM_DADOS";

    private final ExecucaoPipelineRepository execucaoRepository;
    private final ValidacaoRepository validacaoRepository;
    private final QuarentenaRepository quarentenaRepository;

    /** Avalia a qualidade usando os criterios padrao do projeto. */
    @Transactional(readOnly = true)
    public QualidadeResponse avaliar(Long execucaoId) {
        return avaliar(execucaoId, CriteriosQualidade.padrao());
    }

    /** Avalia a qualidade de uma execucao segundo os criterios informados. */
    @Transactional(readOnly = true)
    public QualidadeResponse avaliar(Long execucaoId, CriteriosQualidade criterios) {
        if (!execucaoRepository.existsById(execucaoId)) {
            throw new RecursoNaoEncontradoException("Execucao nao encontrada: " + execucaoId);
        }

        List<Validacao> validacoes = validacaoRepository.findByExecucao_Id(execucaoId);

        long total = 0L, validos = 0L, invalidos = 0L;
        boolean algumaRejeitada = false;
        for (Validacao v : validacoes) {
            total += nz(v.getTotalRegistros());
            validos += nz(v.getRegistrosValidos());
            invalidos += nz(v.getRegistrosInvalidos());
            if (STATUS_REJEITADO.equalsIgnoreCase(v.getStatusFinal())) {
                algumaRejeitada = true;
            }
        }
        String statusValidacao = validacoes.isEmpty()
                ? STATUS_SEM_DADOS
                : (algumaRejeitada ? STATUS_REJEITADO : STATUS_APROVADO);

        long emQuarentena = quarentenaRepository.contarPorExecucaoESituacao(execucaoId, SITUACAO_PENDENTE);

        List<RegraVioladaResponse> regrasVioladas = new ArrayList<>();
        long registrosSeveridadeBloqueante = 0L;
        for (Object[] linha : quarentenaRepository.agruparRegrasVioladas(execucaoId, SITUACAO_PENDENTE)) {
            String nome = (String) linha[0];
            String tipo = (String) linha[1];
            String severidade = (String) linha[2];
            long quantidade = ((Number) linha[3]).longValue();
            regrasVioladas.add(new RegraVioladaResponse(nome, tipo, severidade, quantidade));
            if (criterios.severidadesBloqueantes().contains(severidade)) {
                registrosSeveridadeBloqueante += quantidade;
            }
        }

        double percentualAprovacao = total > 0 ? arredondar(validos * 100.0 / total) : 0.0;
        double percentualQuarentena = total > 0 ? arredondar(emQuarentena * 100.0 / total) : 0.0;

        List<String> motivos = new ArrayList<>();
        if (validacoes.isEmpty()) {
            motivos.add("Execucao sem dados de validacao.");
        } else if (!STATUS_APROVADO.equals(statusValidacao)) {
            motivos.add("Validacao reprovada (status final = " + statusValidacao + ").");
        }
        if (total > 0 && percentualQuarentena > criterios.limiarQuarentenaPercentual()) {
            motivos.add(String.format(Locale.ROOT,
                    "Quarentena em %.2f%% acima do limite de %.2f%%.",
                    percentualQuarentena, criterios.limiarQuarentenaPercentual()));
        }
        if (registrosSeveridadeBloqueante > 0) {
            motivos.add(registrosSeveridadeBloqueante
                    + " registro(s) em quarentena violando regra de severidade bloqueante.");
        }

        boolean aprovado = motivos.isEmpty();

        return new QualidadeResponse(
                execucaoId, total, validos, invalidos, statusValidacao,
                emQuarentena, percentualAprovacao, aprovado,
                regrasVioladas, motivos);
    }

    private static long nz(Long valor) {
        return valor == null ? 0L : valor;
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}