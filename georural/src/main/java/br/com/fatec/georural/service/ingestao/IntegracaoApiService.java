package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ParametrosConsultaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import br.com.fatec.georural.dto.ingestao.TipoAutenticacaoApi;
import br.com.fatec.georural.exception.FalhaComunicacaoApiException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.List;

@Service
public class IntegracaoApiService {

    private final RestClient restClient;

    public IntegracaoApiService(RestClient restClientIngestao) {
        this.restClient = restClientIngestao;
    }

    public RespostaApi consultar(ParametrosConsultaApi p) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(p.endpoint());
        p.filtros().forEach(b::queryParam);

        String headerNome = null;
        String headerValor = null;
        if (p.autenticacao() == TipoAutenticacaoApi.API_KEY && temValor(p.credencial())) {
            String[] parts = p.credencial().split(":", 3); // LOCAL:NOME:VALOR
            if (parts.length == 3) {
                String local = parts[0].trim().toUpperCase();
                String nome = parts[1].trim();
                String valor = parts[2];
                if (local.equals("QUERY")) {
                    b.queryParam(nome, valor);
                } else {
                    headerNome = nome;
                    headerValor = valor;
                }
            }
        }

        URI uri = b.encode().build().toUri();
        final String hNome = headerNome;
        final String hValor = headerValor;

        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(uri);
            spec.headers(h -> {
                if (hNome != null) {
                    h.add(hNome, hValor);
                }
                if (p.autenticacao() == TipoAutenticacaoApi.BASIC && temValor(p.credencial())) {
                    String[] up = p.credencial().split(":", 2); // usuario:senha
                    if (up.length == 2) {
                        h.setBasicAuth(up[0], up[1]);
                    }
                }
                if (p.formato() != null) {
                    h.setAccept(List.of(MediaType.parseMediaType(p.formato().contentType())));
                }
            });

            ResponseEntity<byte[]> resp = spec
                    .retrieve()
                    .onStatus(status -> true, (request, response) -> { })
                    .toEntity(byte[].class);

            String contentType = resp.getHeaders().getContentType() != null
                    ? resp.getHeaders().getContentType().toString()
                    : null;

            return new RespostaApi(resp.getStatusCode().value(), contentType, resp.getBody());

        } catch (RestClientException e) {
            // ausencia de resposta / falha de transporte
            throw new FalhaComunicacaoApiException("Falha ao consultar a API externa: " + detalhe(e), e);
        }
    }

    private static boolean temValor(String s) {
        return s != null && !s.isBlank();
    }

    private static String detalhe(RestClientException e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        if (raiz instanceof SocketTimeoutException || raiz instanceof HttpTimeoutException) {
            return "tempo de resposta esgotado (timeout)";
        }
        if (raiz instanceof ConnectException) {
            return "nao foi possivel conectar ao servico";
        }
        if (raiz instanceof UnknownHostException) {
            return "host nao encontrado";
        }
        return "servico indisponivel";
    }
}