package br.com.fatec.georural.service.qualidade;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.RegraAvaliadaResponse;
import br.com.fatec.georural.entity.RegraQualidade;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.RegraQualidadeRepository;
import br.com.fatec.georural.repository.ValidacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class QualidadeService {

    private static final String SITUACAO_PENDENTE = "PENDENTE";
    private static final String STATUS_APROVADO = "APROVADO";
    private static final String STATUS_REJEITADO = "REJEITADO";
    private static final String STATUS_COM_RESSALVA = "COM_RESSALVA";
    private static final String STATUS_SEM_DADOS = "SEM_DADOS";
    private static final String REGRA_ATIVA = "S";

    private final ExecucaoPipelineRepository execucaoRepository;
    private final ValidacaoRepository validacaoRepository;
    private final QuarentenaRepository quarentenaRepository;
    private final RegraQualidadeRepository regraQualidadeRepository;

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
        // O status olha a proporcao de REGISTROS, nao o status do arquivo.
        //
        // Antes bastava um registro rejeitado entre milhares para o arquivo inteiro
        // virar REJEITADO, e isso reprovava a qualidade direto. O efeito era deixar sem
        // uso as duas valvulas que o proprio sistema tem para esse caso: o limiar
        // configuravel de quarentena (2% por padrao) e a publicacao com ressalva
        // mediante justificativa, no PublicacaoService. Com 2000 registros de fonte
        // publica, zero rejeicoes nao acontece -- nenhuma ingestao grande podia ser
        // publicada nunca.
        //
        // Agora REJEITADO e so quando nao sobrou nada aproveitavel. Sobrando parte, fica
        // COM_RESSALVA e nao reprova sozinho: quem decide sao as duas travas seguintes,
        // o percentual de quarentena acima do limiar e o registro violando regra de
        // severidade bloqueante.
        String statusValidacao;
        if (validacoes.isEmpty()) {
            statusValidacao = STATUS_SEM_DADOS;
        } else if (total > 0 && validos == 0) {
            statusValidacao = STATUS_REJEITADO;
        } else if (algumaRejeitada || invalidos > 0) {
            statusValidacao = STATUS_COM_RESSALVA;
        } else {
            statusValidacao = STATUS_APROVADO;
        }

        long emQuarentena = quarentenaRepository.contarPorExecucaoESituacao(execucaoId, SITUACAO_PENDENTE);

        // regras que retiveram registros nesta execucao
        Map<String, Object[]> violadas = new LinkedHashMap<>();
        long registrosSeveridadeBloqueante = 0L;
        for (Object[] linha : quarentenaRepository.agruparRegrasVioladas(execucaoId, SITUACAO_PENDENTE)) {
            String nome = (String) linha[0];
            String tipo = (String) linha[1];
            String severidade = (String) linha[2];
            long quantidade = ((Number) linha[3]).longValue();
            violadas.put(nome, new Object[]{tipo, severidade, quantidade});
            if (criterios.severidadesBloqueantes().contains(severidade)) {
                registrosSeveridadeBloqueante += quantidade;
            }
        }

        // checklist completo: primeiro as regras violadas, depois as ativas que passaram
        List<RegraAvaliadaResponse> regras = new ArrayList<>();
        for (Map.Entry<String, Object[]> entrada : violadas.entrySet()) {
            Object[] dados = entrada.getValue();
            regras.add(new RegraAvaliadaResponse(
                    entrada.getKey(),
                    (String) dados[0],
                    (String) dados[1],
                    false,
                    ((Number) dados[2]).longValue()));
        }
        for (RegraQualidade regra : regraQualidadeRepository.findByAtiva(REGRA_ATIVA)) {
            if (!violadas.containsKey(regra.getNome())) {
                regras.add(new RegraAvaliadaResponse(
                        regra.getNome(), regra.getTipo(), regra.getSeveridade(), true, 0L));
            }
        }

        double percentualAprovacao = total > 0 ? arredondar(validos * 100.0 / total) : 0.0;
        double percentualQuarentena = total > 0 ? arredondar(emQuarentena * 100.0 / total) : 0.0;

        List<String> motivos = new ArrayList<>();
        if (validacoes.isEmpty()) {
            motivos.add("Execucao sem dados de validacao.");
        } else if (STATUS_REJEITADO.equals(statusValidacao)) {
            motivos.add("Validacao reprovada: nenhum registro aproveitavel (status final = "
                    + statusValidacao + ").");
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
                regras, motivos);
    }

    private static long nz(Long valor) {
        return valor == null ? 0L : valor;
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}