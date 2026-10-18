package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ParametrosConsultaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import br.com.fatec.georural.dto.ingestao.TipoAutenticacaoApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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

        ResponseEntity<byte[]> resp = restClient.get()
                .uri(uri)
                .headers(h -> {
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
                })
                .retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .toEntity(byte[].class);

        String contentType = resp.getHeaders().getContentType() != null
                ? resp.getHeaders().getContentType().toString()
                : null;

        return new RespostaApi(resp.getStatusCode().value(), contentType, resp.getBody());
    }

    private static boolean temValor(String s) {
        return s != null && !s.isBlank();
    }
}