package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import br.com.fatec.georural.dto.ingestao.ResultadoValidacao;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class ValidadorRecebimentoApi {

    private static final long TAMANHO_MAXIMO_BYTES = 512L * 1024 * 1024;

    public ResultadoValidacao validar(RespostaApi resposta,
                                      ConteudoRecebido conteudo,
                                      FormatoRespostaApi formatoEsperado) {
        List<String> problemas = new ArrayList<>();

        if (!resposta.sucesso()) {
            problemas.add("A API respondeu com status HTTP " + resposta.status() + ".");
        }

        if (formatoEsperado == null) {
            problemas.add("Formato nao informado no cadastro do conjunto.");
        }

        if (conteudo.tamanhoBytes() <= 0) {
            problemas.add("Resposta vazia (0 bytes).");
        } else if (!conteudo.bemFormado()) {
            problemas.add("Conteudo nao e um " + nome(formatoEsperado) + " valido.");
        } else if (conteudo.quantidadeRegistros() <= 0) {
            problemas.add("Nenhum registro ou feicao encontrado na resposta.");
        }

        if (conteudo.tamanhoBytes() > TAMANHO_MAXIMO_BYTES) {
            problemas.add("Arquivo acima do limite de " + (TAMANHO_MAXIMO_BYTES / (1024 * 1024)) + " MB.");
        }

        return problemas.isEmpty() ? ResultadoValidacao.ok() : ResultadoValidacao.invalido(problemas);
    }

    private static String nome(FormatoRespostaApi f) {
        return f != null ? f.name() : "formato";
    }
}