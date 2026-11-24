package br.com.fatec.georural.service.auditoria;

import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.security.UsuarioAutenticado;
import br.com.fatec.georural.security.UsuarioAutenticadoProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Diz ao Oracle quem e o usuario desta operacao.
 *
 * O Java sabe quem esta logado, mas a conexao com o banco sempre entra como ADMIN
 * e vem de um pool -- ela e a mesma para qualquer usuario. Quando um gatilho de
 * auditoria dispara, ele esta dentro do Oracle, onde a unica identidade e "ADMIN".
 * Por isso o PKG_AUDIT pergunta SYS_CONTEXT('USERENV','CLIENT_IDENTIFIER'): e um
 * espaco que a aplicacao precisa preencher. Ninguem preenchia, entao toda linha
 * escrita por gatilho saia com usu_id e aud_usuario_nome nulos.
 *
 * O identificador e o keycloakId, que e o que o PKG_AUDIT procura primeiro
 * (usu_keycloak_id), caindo para o e-mail se nao achar.
 *
 * Quando nao ha usuario, o identificador e LIMPO, nao deixado como estava. Isso e
 * essencial: a conexao volta para o pool com o valor da ultima operacao, e sem a
 * limpeza a acao de um usuario seria gravada no nome de outro -- pior do que
 * ficar sem autor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessaoOracle {

    // tamanho de CLIENT_IDENTIFIER no Oracle, e o que o PKG_AUDIT le
    private static final int LIMITE = 64;

    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Chamar no inicio de todo metodo transacional cujo write dispara gatilho de
    // auditoria: VERSAO, REGRA_CALCULO e PARAMETRO_REGRA_CALCULO.
    public void identificarUsuario() {
        aplicar(daRequisicao());
    }

    // Variante para quando o usuario vem do proprio fluxo e nao da requisicao: o
    // pipeline pode rodar disparado pelo Airflow, sem requisicao HTTP, mas o id de
    // quem enviou o arquivo chega por parametro.
    public void identificarUsuario(Long usuarioId) {
        String identificador = null;

        if (usuarioId != null) {
            identificador = usuarioRepository.findById(usuarioId)
                    .map(Usuario::getKeycloakId)
                    .filter(valor -> valor != null && !valor.isBlank())
                    .orElse(null);
        }

        aplicar(identificador != null ? identificador : daRequisicao());
    }

    private String daRequisicao() {
        return usuarioAutenticadoProvider.obterOpcional()
                .map(UsuarioAutenticado::keycloakId)
                .filter(valor -> valor != null && !valor.isBlank())
                .orElse(null);
    }

    private void aplicar(String identificador) {
        try {
            if (identificador == null) {
                entityManager
                        .createNativeQuery("BEGIN DBMS_SESSION.CLEAR_IDENTIFIER(); END;")
                        .executeUpdate();
                return;
            }

            entityManager
                    .createNativeQuery("BEGIN DBMS_SESSION.SET_IDENTIFIER(:ident); END;")
                    .setParameter("ident", identificador.length() > LIMITE
                            ? identificador.substring(0, LIMITE)
                            : identificador)
                    .executeUpdate();
        } catch (Exception e) {
            // A trilha nunca derruba a operacao de negocio.
            log.warn("Nao foi possivel identificar o usuario na sessao Oracle: {}",
                    e.getMessage());
        }
    }
}