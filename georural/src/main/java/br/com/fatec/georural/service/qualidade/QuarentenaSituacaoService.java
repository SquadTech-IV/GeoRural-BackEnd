package br.com.fatec.georural.service.qualidade;

import br.com.fatec.georural.dto.response.qualidade.QuarentenaConsultaResponse;
import br.com.fatec.georural.entity.Quarentena;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.security.IsolamentoCargaService;
import br.com.fatec.georural.service.auditoria.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuarentenaSituacaoService {

    private static final Set<String> SITUACOES_FINAIS = Set.of("CORRIGIDO", "DESCARTADO");

    private final QuarentenaRepository quarentenaRepository;
    private final AuditoriaService auditoriaService;
    private final IsolamentoCargaService isolamentoCargaService;

    @Transactional
    public QuarentenaConsultaResponse alterarSituacao(Long quaId, String novaSituacaoRaw) {
        if (novaSituacaoRaw == null || novaSituacaoRaw.isBlank()) {
            throw new RequisicaoInvalidaException("Informe qua_situacao (CORRIGIDO ou DESCARTADO).");
        }

        String novaSituacao = novaSituacaoRaw.trim().toUpperCase();
        if (!SITUACOES_FINAIS.contains(novaSituacao)) {
            throw new RequisicaoInvalidaException(
                    "Situação inválida: '" + novaSituacao + "'. Transições finais aceitas: CORRIGIDO ou DESCARTADO.");
        }

        Quarentena q = quarentenaRepository.findById(quaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro de quarentena não encontrado: " + quaId));

        // Aplica regra de isolamento de carga (OPERADOR so altera item dos seus proprios arquivos)
        isolamentoCargaService.validarAcessoQuarentena(quaId);

        String situacaoAnterior = q.getSituacao();
        if (situacaoAnterior == null || !"PENDENTE".equalsIgnoreCase(situacaoAnterior.trim())) {
            throw new RequisicaoInvalidaException(
                    "Registro de quarentena já está em situação final ('" + situacaoAnterior + "') e não pode ser alterado.");
        }

        LocalDateTime resolucaoAnterior = q.getDataResolucao();
        LocalDateTime agora = LocalDateTime.now();

        q.setSituacao(novaSituacao);
        q.setDataResolucao(agora);

        Quarentena salvo = quarentenaRepository.save(q);
        registrarAuditoria(salvo, situacaoAnterior, resolucaoAnterior, agora);

        var val = salvo.getValidacao();
        var exec = val != null ? val.getExecucao() : null;
        var arq = val != null ? val.getArquivoBruto() : null;
        var conj = arq != null ? arq.getConjunto() : (exec != null ? exec.getConjunto() : null);

        return QuarentenaConsultaResponse.builder()
                .quarentenaId(salvo.getId())
                .execucaoId(exec != null ? exec.getId() : null)
                .conjuntoId(conj != null ? conj.getId() : null)
                .nomeConjunto(conj != null ? conj.getNome() : null)
                .arquivoBrutoId(arq != null ? arq.getId() : null)
                .nomeArquivoBruto(arq != null ? arq.getNome() : null)
                .linhaOrigem(salvo.getLinhaOrigem())
                .colunaErro(salvo.getColunaErro())
                .codigoRegra(salvo.getRegra() != null ? salvo.getRegra().getTipo() : null)
                .nomeRegra(salvo.getRegra() != null ? salvo.getRegra().getNome() : null)
                .motivoErro(salvo.getMotivoErro())
                .dadoOriginal(salvo.getDadoOriginal())
                .situacao(salvo.getSituacao())
                .dataRegistro(salvo.getDataRegistro())
                .build();
    }

    private void registrarAuditoria(
            Quarentena q,
            String situacaoAnterior,
            LocalDateTime resolucaoAnterior,
            LocalDateTime dataHora) {

        String dadosAntigos = String.format(
                "{\"qua_situacao\":\"%s\",\"qua_data_resolucao\":\"%s\"}",
                AuditoriaService.escapar(situacaoAnterior),
                resolucaoAnterior != null ? resolucaoAnterior.toString() : ""
        );
        String dadosNovos = String.format(
                "{\"qua_situacao\":\"%s\",\"qua_data_resolucao\":\"%s\"}",
                AuditoriaService.escapar(q.getSituacao()),
                q.getDataResolucao() != null ? q.getDataResolucao().toString() : ""
        );
        auditoriaService.registrar("ATUALIZACAO_SITUACAO", "QUARENTENA", q.getId(), dadosAntigos, dadosNovos, dataHora);
    }
}