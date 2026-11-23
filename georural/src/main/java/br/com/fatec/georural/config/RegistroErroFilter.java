package br.com.fatec.georural.config;

import br.com.fatec.georural.service.monitoramento.RegistroErroService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// unico ponto de gravacao de erro: fica por fora de tudo e olha o status final.
// Pega tambem o que nao passa pelo GlobalExceptionHandler, como 404 de rota
// inexistente e falha dentro de outro filtro.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RegistroErroFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_EXCECAO = "georural.erro.excecao";
    public static final String ATRIBUTO_MENSAGEM = "georural.erro.mensagem";
    public static final String ATRIBUTO_PILHA = "georural.erro.pilha";
    public static final String ATRIBUTO_CORRELACAO = "georural.erro.correlacao";

    private final RegistroErroService registro;

    @Value("${georural.monitoramento.erros.registrar-status-minimo:500}")
    private int statusMinimo;

    @Value("${georural.monitoramento.erros.ativo:true}")
    private boolean ativo;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest pedido,
                                    @NonNull HttpServletResponse resposta,
                                    @NonNull FilterChain cadeia)
            throws ServletException, IOException {

        String correlacao = UUID.randomUUID().toString().substring(0, 8);
        pedido.setAttribute(ATRIBUTO_CORRELACAO, correlacao);
        long inicio = System.nanoTime();
        try {
            cadeia.doFilter(pedido, resposta);
        } finally {
            int status = resposta.getStatus();
            // fora de 100..599 o CHECK da tabela recusaria: acontece quando a
            // requisicao morre antes de ter resposta
            if (ativo && statusMinimo > 0 && status >= statusMinimo
                    && status >= 100 && status <= 599) {
                gravar(pedido, resposta, correlacao, (System.nanoTime() - inicio) / 1_000_000L);
            }
        }
    }

    private void gravar(HttpServletRequest pedido, HttpServletResponse resposta,
                        String correlacao, long duracaoMs) {
        registro.registrar(new RegistroErroService.ErroDeRequisicao(
                pedido.getMethod(),
                // so o caminho: a query string pode levar token ou dado pessoal
                pedido.getRequestURI(),
                resposta.getStatus(),
                texto(pedido.getAttribute(ATRIBUTO_EXCECAO)),
                texto(pedido.getAttribute(ATRIBUTO_MENSAGEM)),
                texto(pedido.getAttribute(ATRIBUTO_PILHA)),
                ipDe(pedido),
                correlacao,
                duracaoMs));
    }

    // nao registra o proprio monitoramento: a tela recarregando com o banco fora
    // encheria a tabela de erros sobre a tabela de erros
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest pedido) {
        String rota = pedido.getRequestURI();
        return rota != null && rota.startsWith("/api/monitoramento");
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static String ipDe(HttpServletRequest pedido) {
        // atras do balanceador da US#11 o IP real vem no cabecalho
        String encaminhado = pedido.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            int virgula = encaminhado.indexOf(',');
            return virgula > 0 ? encaminhado.substring(0, virgula).trim() : encaminhado.trim();
        }
        return pedido.getRemoteAddr();
    }
}
