package br.com.fatec.georural.security;

import br.com.fatec.georural.repository.QuarentenaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IsolamentoCargaService {

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final QuarentenaRepository quarentenaRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Devolve o usu_id do usuario autenticado pelo token JWT (impede falsificacao por parametro/body).
     * Se nao houver usuario autenticado (ex: modo liberar-api=true em dev), devolve o fallback informado.
     */
    public Long resolverUsuarioIdAutenticado(Long fallback) {
        return usuarioAutenticadoProvider.obterOpcional()
                .map(UsuarioAutenticado::usuId)
                .orElse(fallback);
    }

    /**
     * Valida se o usuario autenticado pode operar o arquivo bruto (e sua carga raiz).
     * ADMIN e GESTOR podem operar qualquer arquivo.
     * OPERADOR so pode operar arquivos cuja carga raiz pertenca a ele proprio.
     */
    public void validarAcessoArquivoBruto(Long arquivoBrutoId) {
        if (arquivoBrutoId == null) {
            return;
        }
        UsuarioAutenticado usuario = usuarioAutenticadoProvider.obterOpcional().orElse(null);
        if (usuario == null || !usuario.isOperador()) {
            return;
        }

        Long donoRaizId = quarentenaRepository.findDonoArquivoRaizIdPorArquivo(arquivoBrutoId);
        if (donoRaizId != null && !donoRaizId.equals(usuario.usuId())) {
            log.warn("Isolamento de carga negado: OPERADOR usu_id={} tentou acessar arquivoBrutoId={} (donoRaiz={})",
                    usuario.usuId(), arquivoBrutoId, donoRaizId);
            throw new AccessDeniedException(
                    "Operador só pode reprocessar ou alterar arquivos enviados por ele mesmo.");
        }
    }

    /**
     * Valida se o usuario autenticado pode operar uma lista de arquivos brutos (ex: lote do pipeline).
     */
    public void validarAcessoArquivosBrutos(List<Long> arquivoBrutoIds) {
        if (arquivoBrutoIds == null || arquivoBrutoIds.isEmpty()) {
            return;
        }
        for (Long id : arquivoBrutoIds) {
            validarAcessoArquivoBruto(id);
        }
    }

    /**
     * Valida se o usuario autenticado pode operar a rodada (dagRunId) no pipeline.
     */
    public void validarAcessoRodada(String dagRunId) {
        if (dagRunId == null || dagRunId.isBlank()) {
            return;
        }
        UsuarioAutenticado usuario = usuarioAutenticadoProvider.obterOpcional().orElse(null);
        if (usuario == null || !usuario.isOperador()) {
            return;
        }

        List<Long> arquivosDaRodada = jdbcTemplate.queryForList(
                "SELECT DISTINCT arq_bruto_id FROM EXECUCAO_PIPELINE WHERE exe_dag_run_id = ? AND arq_bruto_id IS NOT NULL",
                Long.class,
                dagRunId.trim()
        );
        validarAcessoArquivosBrutos(arquivosDaRodada);
    }

    /**
     * Valida se o usuario autenticado pode alterar o registro de quarentena (qua_id).
     * ADMIN e GESTOR podem alterar qualquer item.
     * OPERADOR so pode alterar registros vinculados a arquivos enviados por ele proprio.
     */
    public void validarAcessoQuarentena(Long quaId) {
        if (quaId == null) {
            return;
        }
        UsuarioAutenticado usuario = usuarioAutenticadoProvider.obterOpcional().orElse(null);
        if (usuario == null || !usuario.isOperador()) {
            return;
        }

        Long donoRaizId = quarentenaRepository.findDonoArquivoRaizIdPorQuarentena(quaId);
        if (donoRaizId != null && !donoRaizId.equals(usuario.usuId())) {
            log.warn("Isolamento de carga negado: OPERADOR usu_id={} tentou alterar quarentena qua_id={} (donoRaiz={})",
                    usuario.usuId(), quaId, donoRaizId);
            throw new AccessDeniedException(
                    "Operador só pode alterar registros de quarentena dos seus próprios arquivos.");
        }
    }
}