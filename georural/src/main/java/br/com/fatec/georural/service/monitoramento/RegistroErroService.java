package br.com.fatec.georural.service.monitoramento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

// grava um erro de requisicao em ERRO_API. Nunca propaga excecao: se o registro
// falhar, o pedido original nao pode ser afetado por causa disso
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroErroService {

    private static final int MAX_MENSAGEM = 2000;
    private static final int MAX_PILHA = 4000;
    private static final int MAX_ROTA = 500;
    private static final int MAX_EXCECAO = 200;

    private final JdbcTemplate jdbc;

    @Value("${georural.monitoramento.erros.retencao-dias:30}")
    private int retencaoDias;

    // a limpeza roda no caminho de gravacao, no maximo uma vez por hora:
    // o projeto nao tem @EnableScheduling e ligar o agendador so para isto
    // mudaria o comportamento da aplicacao inteira
    private final AtomicReference<LocalDateTime> ultimaLimpeza = new AtomicReference<>();

    public void registrar(ErroDeRequisicao erro) {
        try {
            jdbc.update(
                    " INSERT INTO ERRO_API (err_metodo, err_rota, err_status, err_excecao, "
                  + "                      err_mensagem, err_pilha, err_ip_origem, "
                  + "                      err_correlacao, err_duracao_ms) "
                  + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ",
                    cortar(erro.metodo(), 10),
                    cortar(erro.rota(), MAX_ROTA),
                    erro.status(),
                    cortar(erro.excecao(), MAX_EXCECAO),
                    cortar(erro.mensagem(), MAX_MENSAGEM),
                    cortar(erro.pilha(), MAX_PILHA),
                    cortar(erro.ipOrigem(), 45),
                    cortar(erro.correlacao(), 64),
                    erro.duracaoMs());
            limparSePassouAHora();
        } catch (RuntimeException e) {
            log.warn("Monitoramento: nao foi possivel registrar o erro de {} {}: {}",
                    erro.metodo(), erro.rota(), e.getMessage());
        }
    }

    private void limparSePassouAHora() {
        if (retencaoDias <= 0) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime ultima = ultimaLimpeza.get();
        if (ultima != null && Duration.between(ultima, agora).toMinutes() < 60) {
            return;
        }
        if (!ultimaLimpeza.compareAndSet(ultima, agora)) {
            return;
        }
        try {
            int apagados = jdbc.update(
                    // NUMTODSINTERVAL porque em Oracle TIMESTAMP menos NUMBER nao e valido;
                    // subtrair numero so funciona com DATE
                    "DELETE FROM ERRO_API "
                  + " WHERE err_data_hora < SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY')",
                    retencaoDias);
            if (apagados > 0) {
                log.info("Monitoramento: {} erro(s) com mais de {} dia(s) apagado(s).",
                        apagados, retencaoDias);
            }
        } catch (RuntimeException e) {
            log.warn("Monitoramento: falha ao limpar ERRO_API: {}", e.getMessage());
        }
    }

    private static String cortar(String valor, int limite) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= limite ? valor : valor.substring(0, limite);
    }

    // dados de um erro a registrar
    public record ErroDeRequisicao(
            String metodo,
            String rota,
            int status,
            String excecao,
            String mensagem,
            String pilha,
            String ipOrigem,
            String correlacao,
            Long duracaoMs
    ) {
    }
}
