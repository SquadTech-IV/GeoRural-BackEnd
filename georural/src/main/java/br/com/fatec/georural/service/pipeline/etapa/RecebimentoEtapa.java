package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import br.com.fatec.georural.service.pipeline.inventario.InventarioArquivoService;
import br.com.fatec.georural.service.pipeline.validacao.ProblemaFeicao;
import br.com.fatec.georural.service.pipeline.validacao.RegistroQuarentenaService;
import br.com.fatec.georural.service.pipeline.validacao.RegistroQuarentenaService.RegistroPendente;
import br.com.fatec.georural.service.pipeline.validacao.RegrasQualidade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// etapa 1 - INGESTAO
@Slf4j
@Component
@RequiredArgsConstructor
public class RecebimentoEtapa implements EtapaPipeline {

    private final ArquivoBrutoRepository arquivoRepository;
    private final InventarioArquivoService inventario;
    private final RegistroQuarentenaService quarentena;

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

        // reconsulta os membros para refletir o estado real, nao o delta do retry
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

        long naoAbertos = registrarNaoAbertos(ctx, arquivo, membros);

        long naoSuportados = membros.stream()
                .filter(m -> !suportado(m.getFormatoDetectado()))
                .count();

        return ResultadoEtapa.de(membros.size(), naoSuportados,
                membros.size() + " conjunto(s) de dados dentro de '" + arquivo.getNome()
                        + "', competencia " + arquivo.getCompetencia()
                        + (naoSuportados > 0 ? " - " + naoSuportados + " em formato nao suportado" : "")
                        + (naoAbertos > 0 ? " - " + naoAbertos
                           + " pacote(s) nao aberto(s), ver quarentena" : "")
                        + ".");
    }

    // registra na quarentena o pacote que o inventario nao abriu
    private long registrarNaoAbertos(ContextoExecucao ctx, ArquivoBruto pai,
                                     List<ArquivoBruto> membros) {
        List<ArquivoBruto> naoAbertos = membros.stream()
                .filter(m -> "FALHA".equals(m.getStatus()))
                .toList();
        if (naoAbertos.isEmpty() || ctx.getExecucaoIdAtual() == null) {
            return 0;
        }

        Long validacaoId = quarentena.abrirValidacao(pai.getId(), ctx.getExecucaoIdAtual());
        List<RegistroPendente> pendentes = naoAbertos.stream()
                .map(m -> new RegistroPendente(
                        m.getCaminhoInterno() != null ? m.getCaminhoInterno() : m.getNome(),
                        null, null,
                        ProblemaFeicao.erro(RegrasQualidade.FORMATO_NAO_SUPORTADO, "pacote",
                                "pacote nao aberto", "pacote abrivel dentro do limite de niveis",
                                "O pacote '" + m.getNome() + "' estava dentro de '" + pai.getNome()
                                        + "' e nao pode ser aberto pelo inventario. As camadas de "
                                        + "dentro dele nao foram catalogadas.")))
                .toList();

        quarentena.registrarLote(validacaoId, "INGESTAO", pendentes);
        quarentena.fecharValidacao(validacaoId, membros.size(),
                membros.size() - naoAbertos.size(), naoAbertos.size());

        log.warn("{} pacote(s) dentro de '{}' nao foram abertos; registrados na quarentena.",
                naoAbertos.size(), pai.getNome());
        return naoAbertos.size();
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
