package br.com.fatec.georural.service.qualidade;

import br.com.fatec.georural.dto.response.qualidade.QuarentenaConsultaResponse;
import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.Quarentena;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.AuditoriaRepository;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.security.IsolamentoCargaService;
import br.com.fatec.georural.security.UsuarioAutenticado;
import br.com.fatec.georural.security.UsuarioAutenticadoProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuarentenaSituacaoService {

    private static final Set<String> SITUACOES_FINAIS = Set.of("CORRIGIDO", "DESCARTADO");

    private final QuarentenaRepository quarentenaRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final IsolamentoCargaService isolamentoCargaService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

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

        Auditoria aud = new Auditoria();
        aud.setOperacao("ATUALIZACAO_SITUACAO");
        aud.setEntidadeAfetada("QUARENTENA");
        aud.setRegistroId(q.getId());
        aud.setDataHora(dataHora);
        aud.setIpOrigem(extrairIpAtual());

        UsuarioAutenticado auth = usuarioAutenticadoProvider.obterOpcional().orElse(null);
        if (auth != null && auth.usuId() != null) {
            Usuario usuario = usuarioRepository.findById(auth.usuId()).orElse(null);
            if (usuario != null) {
                aud.setUsuario(usuario);
                aud.setUsuarioNome(usuario.getNome());
            } else {
                aud.setUsuarioNome(auth.keycloakId());
            }
        } else {
            aud.setUsuarioNome("sistema");
        }

        aud.setDadosAntigos(String.format(
                "{\"qua_situacao\":\"%s\",\"qua_data_resolucao\":\"%s\"}",
                escapar(situacaoAnterior),
                resolucaoAnterior != null ? resolucaoAnterior.toString() : ""
        ));

        aud.setDadosNovos(String.format(
                "{\"qua_situacao\":\"%s\",\"qua_data_resolucao\":\"%s\"}",
                escapar(q.getSituacao()),
                q.getDataResolucao() != null ? q.getDataResolucao().toString() : ""
        ));

        auditoriaRepository.save(aud);
    }

    private String extrairIpAtual() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}