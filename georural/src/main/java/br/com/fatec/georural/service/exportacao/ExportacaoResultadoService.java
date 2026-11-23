package br.com.fatec.georural.service.exportacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

// Monta o arquivo direto no fluxo de saida da resposta.
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportacaoResultadoService {

    private static final int BUFFER = 64 * 1024;

    private final ConsultaExportacaoService consulta;
    private final RegistroExportacaoAuditoria auditoria;

    public String nomeDoArquivo(ConsultaExportacaoService.Versao v, String indicador,
                                FormatoExportacao formato) {
        String parte = (indicador == null || indicador.isBlank())
                ? "todos" : indicador.trim().toUpperCase();
        String competencia = v.competencia() == null ? "sem-competencia" : v.competencia();
        return "georural_v" + v.numero() + "_" + competencia + "_" + parte + "." + formato.extensao();
    }

    public void escrever(OutputStream saida, ConsultaExportacaoService.Versao versao,
                         String indicador, FormatoExportacao formato,
                         SeparadorCsv separador, boolean comGeometria) throws IOException {

        boolean geometria = comGeometria || formato.exigeGeometria();
        long[] contagem = {0};

        Writer w = new BufferedWriter(new OutputStreamWriter(saida, StandardCharsets.UTF_8), BUFFER);
        try (EscritorExportacao escritor = escritorDe(formato, w, separador, geometria)) {
            escritor.inicio();
            consulta.paraCadaLinha(versao.id(), indicador, geometria, linha -> {
                try {
                    escritor.linha(linha);
                    contagem[0]++;
                } catch (IOException e) {
                    throw new FluxoInterrompido(e);
                }
            });
            escritor.fim();
        } catch (FluxoInterrompido e) {
            log.warn("Exportacao da versao {} interrompida depois de {} linha(s): {}",
                    versao.id(), contagem[0], e.getCause().getMessage());
            return;
        }

        auditoria.registrar(versao, indicador, formato, geometria, contagem[0]);
        log.info("Exportacao da versao {} concluida: {} linha(s) em {}",
                versao.id(), contagem[0], formato);
    }

    private EscritorExportacao escritorDe(FormatoExportacao formato, Writer w,
                                          SeparadorCsv separador, boolean geometria) {
        return switch (formato) {
            case CSV     -> new EscritorCsv(w, separador, geometria);
            case JSON    -> new EscritorJson(w, geometria);
            case GEOJSON -> new EscritorGeoJson(w);
        };
    }

    private static class FluxoInterrompido extends RuntimeException {
        FluxoInterrompido(IOException causa) { super(causa); }
    }
}
