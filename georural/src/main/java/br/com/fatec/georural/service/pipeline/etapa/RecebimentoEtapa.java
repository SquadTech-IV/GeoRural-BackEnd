package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import br.com.fatec.georural.service.pipeline.inventario.InventarioArquivoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecebimentoEtapa implements EtapaPipeline {

    private final ArquivoBrutoRepository arquivoRepository;
    private final InventarioArquivoService inventario;

    @Override
    public Etapa etapa() {
        return Etapa.INGESTAO;
    }

    @Override
    @Transactional
    public ResultadoEtapa executar(ContextoExecucao ctx) {
        if (ctx.getArquivoBrutoId() == null) {
            throw new IllegalArgumentException(
                    "A etapa INGESTAO precisa do arquivoBrutoId: o arquivo ja deve estar na zona bruta.");
        }

        ArquivoBruto arquivo = arquivoRepository.findById(ctx.getArquivoBrutoId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Arquivo bruto nao encontrado: " + ctx.getArquivoBrutoId()));

        if (ctx.getCompetencia() == null) {
            ctx.setCompetencia(arquivo.getCompetencia());
        }
        ctx.comParametro("arquivo", arquivo.getNome());
        ctx.comParametro("canal", arquivo.getCanalEntrada());

        inventario.inventariar(arquivo);

        List<ArquivoBruto> membros = arquivoRepository.findByPai_IdOrderByCaminhoInternoAsc(arquivo.getId());

        if (membros.isEmpty()) {
            String formato = arquivo.getFormatoDetectado() != null
                    ? arquivo.getFormatoDetectado() : "nao detectado";
            boolean suportado = suportado(arquivo.getFormatoDetectado());
            return ResultadoEtapa.de(1, suportado ? 0 : 1,
                    "Arquivo unico '" + arquivo.getNome() + "' (" + formato + "), competencia "
                            + arquivo.getCompetencia()
                            + (suportado ? "." : " - formato nao suportado."));
        }

        long naoSuportados = membros.stream()
                .filter(m -> !suportado(m.getFormatoDetectado()))
                .count();

        return ResultadoEtapa.de(membros.size(), naoSuportados,
                membros.size() + " conjunto(s) de dados dentro de '" + arquivo.getNome()
                        + "', competencia " + arquivo.getCompetencia()
                        + (naoSuportados > 0 ? " - " + naoSuportados + " em formato nao suportado." : "."));
    }

    private boolean suportado(String formatoDetectado) {
        if (formatoDetectado == null) {
            return false;
        }
        try {
            return FormatoArquivo.valueOf(formatoDetectado).suportado();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
