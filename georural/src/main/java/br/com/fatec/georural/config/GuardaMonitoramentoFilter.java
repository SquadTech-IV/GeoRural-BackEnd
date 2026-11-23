package br.com.fatec.georural.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// trava provisoria do /api/monitoramento. So vale se georural.monitoramento.token
// estiver definido; vazio, nao trava nada e o comportamento local continua igual
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GuardaMonitoramentoFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Monitoramento-Token";
    private static final String PREFIXO = "/api/monitoramento";

    @Value("${georural.monitoramento.token:}")
    private String token;

    @PostConstruct
    void avisar() {
        if (token == null || token.isBlank()) {
            log.warn("Monitoramento: /api/monitoramento esta SEM trava "
                    + "(georural.monitoramento.token vazio). Em ambiente publico defina o token "
                    + "ou deixe a rota fora do que o balanceador expoe: ela mostra rotas, "
                    + "mensagens de erro e IP de origem.");
        } else {
            log.info("Monitoramento: /api/monitoramento exige o cabecalho {}.", CABECALHO);
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest pedido) {
        String rota = pedido.getRequestURI();
        return rota == null || !rota.startsWith(PREFIXO);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest pedido,
                                    @NonNull HttpServletResponse resposta,
                                    @NonNull FilterChain cadeia)
            throws ServletException, IOException {

        if (token == null || token.isBlank() || liberado(pedido)) {
            cadeia.doFilter(pedido, resposta);
            return;
        }
        recusar(pedido, resposta);
    }

    private boolean liberado(HttpServletRequest pedido) {
        // preflight do CORS nao leva cabecalho proprio
        if ("OPTIONS".equalsIgnoreCase(pedido.getMethod())) {
            return true;
        }
        String enviado = pedido.getHeader(CABECALHO);
        if (enviado == null) {
            return false;
        }
        // comparacao de tempo constante, para nao vazar o token por medida de tempo
        return MessageDigest.isEqual(enviado.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private void recusar(HttpServletRequest pedido, HttpServletResponse resposta)
            throws IOException {
        log.warn("Monitoramento: acesso recusado a {} {} (sem o cabecalho {}).",
                pedido.getMethod(), pedido.getRequestURI(), CABECALHO);
        resposta.setStatus(HttpStatus.UNAUTHORIZED.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        resposta.getWriter().write("{\"status\":401,\"erro\":\"Unauthorized\","
                + "\"mensagem\":\"Informe o cabecalho " + CABECALHO + ".\","
                + "\"caminho\":\"" + PREFIXO + "\"}");
    }
}
