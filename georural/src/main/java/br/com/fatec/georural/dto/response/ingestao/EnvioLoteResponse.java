package br.com.fatec.georural.dto.response.ingestao;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.service.ingestao.SituacaoEnvio;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// resultado de um envio em lote: totais + o desfecho de cada arquivo
public record EnvioLoteResponse(
        String envioId,
        LocalDateTime recebidoEm,
        long duracaoSegundos,
        Long conjuntoId,
        String competencia,
        Totais totais,
        List<ArquivoValidacaoResponse> itens
) {

    // contagens do envio, para a tela nao somar na mao
    public record Totais(
            int enviados,
            int aceitos,
            int duplicados,
            int recusados,
            long bytesEnviados,
            long bytesAceitos
    ) {
    }

    // monta os totais contando os itens
    public static EnvioLoteResponse de(String envioId, LocalDateTime inicio, Long conjuntoId,
                                       String competencia, List<ArquivoValidacaoResponse> itens,
                                       long bytesEnviados, long bytesAceitos) {
        int aceitos = (int) itens.stream().filter(i -> i.situacao() == SituacaoEnvio.ACEITO).count();
        int duplicados = (int) itens.stream().filter(i -> i.situacao() == SituacaoEnvio.DUPLICADO).count();
        int recusados = itens.size() - aceitos - duplicados;

        LocalDateTime fim = LocalDateTime.now();
        return new EnvioLoteResponse(
                envioId,
                inicio,
                Duration.between(inicio, fim).toSeconds(),
                conjuntoId,
                competencia,
                new Totais(itens.size(), aceitos, duplicados, recusados, bytesEnviados, bytesAceitos),
                itens);
    }

    // nenhum arquivo entrou
    public boolean nadaEntrou() {
        return totais.aceitos() == 0;
    }

    // algum arquivo pede acao de quem enviou (duplicado nao conta)
    public boolean temProblema() {
        return totais.recusados() > 0;
    }
}
