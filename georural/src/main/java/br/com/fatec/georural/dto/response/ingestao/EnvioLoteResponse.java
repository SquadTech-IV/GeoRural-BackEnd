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
        Long responsavelId,
        String competencia,
        Totais totais,
        List<ArquivoValidacaoResponse> itens,
        // preenchido so quando o envio pediu para processar na mesma chamada
        ProcessamentoDoEnvioResponse processamento
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

    // monta os totais a partir dos itens
    public static EnvioLoteResponse de(String envioId, LocalDateTime inicio, Long conjuntoId,
                                       Long responsavelId, String competencia,
                                       List<ArquivoValidacaoResponse> itens) {
        int aceitos = (int) itens.stream().filter(i -> i.situacao() == SituacaoEnvio.ACEITO).count();
        int duplicados = (int) itens.stream().filter(i -> i.situacao() == SituacaoEnvio.DUPLICADO).count();
        int recusados = itens.size() - aceitos - duplicados;

        long bytesEnviados = itens.stream().mapToLong(ArquivoValidacaoResponse::tamanhoBytes).sum();
        long bytesAceitos = itens.stream().filter(ArquivoValidacaoResponse::aceito)
                .mapToLong(ArquivoValidacaoResponse::tamanhoBytes).sum();

        return new EnvioLoteResponse(
                envioId,
                inicio,
                Duration.between(inicio, LocalDateTime.now()).toSeconds(),
                conjuntoId,
                responsavelId,
                competencia,
                new Totais(itens.size(), aceitos, duplicados, recusados, bytesEnviados, bytesAceitos),
                itens,
                null);
    }

    // copia com o resultado do pipeline anexado
    public EnvioLoteResponse comProcessamento(ProcessamentoDoEnvioResponse processamento) {
        return new EnvioLoteResponse(envioId, recebidoEm, duracaoSegundos, conjuntoId,
                responsavelId, competencia, totais, itens, processamento);
    }

    // nenhum arquivo entrou
    public boolean nadaEntrou() {
        return totais.aceitos() == 0;
    }

    // algum arquivo pede acao de quem enviou
    public boolean temProblema() {
        return totais.recusados() > 0;
    }
}
