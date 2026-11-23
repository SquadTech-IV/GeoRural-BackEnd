package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.exportacao.PreviaExportacaoResponse;
import br.com.fatec.georural.service.exportacao.ConsultaExportacaoService;
import br.com.fatec.georural.service.exportacao.ExportacaoResultadoService;
import br.com.fatec.georural.service.exportacao.FormatoExportacao;
import br.com.fatec.georural.service.exportacao.SeparadorCsv;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/versoes/{versaoId}/resultados")
@RequiredArgsConstructor
public class ExportacaoResultadoController {

    private final ConsultaExportacaoService consulta;
    private final ExportacaoResultadoService exportacao;

    // Quanto vem, antes de baixar.
    @GetMapping("/previa")
    public PreviaExportacaoResponse previa(
            @PathVariable Long versaoId,
            @RequestParam(required = false) String indicador,
            @RequestParam(required = false, defaultValue = "CSV") String formato,
            @RequestParam(required = false, defaultValue = "false") boolean geometria) {

        ConsultaExportacaoService.Versao v = consulta.exigirPublicada(versaoId);
        FormatoExportacao f = FormatoExportacao.de(formato);
        boolean comGeo = geometria || f.exigeGeometria();
        long[] c = consulta.contar(versaoId, indicador);

        long linhas = c[0];
        long imoveis = c[1];
        long saida = f == FormatoExportacao.GEOJSON ? imoveis : linhas;
        long bytes = saida * (comGeo ? 9_000L : 180L);

        return new PreviaExportacaoResponse(
                v.id(), v.numero(), v.competencia(), v.situacao(), v.conjunto(),
                indicador == null || indicador.isBlank() ? null : indicador.trim().toUpperCase(),
                saida, imoveis, comGeo, legivel(bytes), aviso(saida, comGeo));
    }

    @GetMapping("/download")
    public ResponseEntity<StreamingResponseBody> baixar(
            @PathVariable Long versaoId,
            @RequestParam(required = false) String indicador,
            @RequestParam(required = false, defaultValue = "CSV") String formato,
            @RequestParam(required = false, defaultValue = "false") boolean geometria,
            @RequestParam(required = false, defaultValue = "PONTO_E_VIRGULA") String separador) {

        ConsultaExportacaoService.Versao v = consulta.exigirPublicada(versaoId);
        FormatoExportacao f = FormatoExportacao.de(formato);
        SeparadorCsv s = SeparadorCsv.de(separador);

        StreamingResponseBody corpo =
                saida -> exportacao.escrever(saida, v, indicador, f, s, geometria);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + exportacao.nomeDoArquivo(v, indicador, f) + "\"")

                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(corpo);
    }

    private String legivel(long bytes) {
        String[] u = {"B", "KB", "MB", "GB"};
        double v = bytes;
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format("~%.1f %s", v, u[i]);
    }

    private String aviso(long linhas, boolean geometria) {
        if (geometria && linhas > 20_000) {
            return "Exportacao grande com geometria: o download leva minutos e nao cabe no limite de 3s "
                 + "que a US#30.04 fiscaliza. E streaming, entao nao ocupa memoria do servidor, "
                 + "mas nao abra pelo navegador em aba que voce va fechar.";
        }
        if (linhas > 100_000) {
            return "Mais de 100 mil linhas: planilha nao abre tudo. Considere filtrar por indicador.";
        }
        return null;
    }
}
