package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.dto.response.ingestao.EnvioLoteResponse;
import br.com.fatec.georural.dto.response.ingestao.ProcessamentoDoEnvioResponse;
import br.com.fatec.georural.service.pipeline.LoteProcessamentoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

// roda o pipeline nos arquivos que acabaram de ser aceitos no envio
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessamentoPosEnvioService {

    private final LoteProcessamentoService lote;

    public ProcessamentoDoEnvioResponse rodar(EnvioLoteResponse envio) {
        List<Long> aceitos = envio.itens().stream()
                .filter(ArquivoValidacaoResponse::aceito)
                .map(ArquivoValidacaoResponse::arquivoId)
                .filter(id -> id != null)
                .toList();

        if (aceitos.isEmpty()) {
            return ProcessamentoDoEnvioResponse.nadaParaRodar();
        }

        log.info("Envio {}: disparando o pipeline para {} arquivo(s) aceito(s).",
                envio.envioId(), aceitos.size());

        // false no pararNoPrimeiroErro: um arquivo que falha nao impede o outro de rodar,
        // igual ao criterio do envio
        LoteProcessamentoService.Resultado resultado = lote.processar(
                envio.conjuntoId(), null, envio.competencia(), aceitos, false, null);

        List<ProcessamentoDoEnvioResponse.Item> itens = resultado.arquivos().stream()
                .map(a -> new ProcessamentoDoEnvioResponse.Item(
                        a.arquivoBrutoId(), a.nome(), a.dagRunId(), a.situacao(),
                        a.versaoGerada(), a.mensagem()))
                .toList();

        String mensagem = resultado.interrupcao() != null
                ? resultado.interrupcao()
                : resultado.concluidos() + " arquivo(s) processado(s)"
                + (resultado.falhados() > 0 ? " e " + resultado.falhados() + " com falha" : "")
                + ". As versoes ficam como RASCUNHO ate alguem publicar.";

        return new ProcessamentoDoEnvioResponse(aceitos.size(), resultado.concluidos(),
                resultado.falhados(), mensagem, itens);
    }
}
