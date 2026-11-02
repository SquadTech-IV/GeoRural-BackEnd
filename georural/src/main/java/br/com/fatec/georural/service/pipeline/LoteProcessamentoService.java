package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// roda o pipeline para vários arquivos, um de cada vez
@Slf4j
@Service
@RequiredArgsConstructor
public class LoteProcessamentoService {

    private final PipelineExecutor executor;
    private final ArquivoBrutoRepository arquivoRepository;

    // sem transacao propria: cada arquivo abre e fecha as dele
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Resultado processar(Long conjuntoId, Long usuarioId, String competencia,
                              List<Long> arquivoIds, boolean pararNoPrimeiroErro,
                              Map<String, String> parametros) {

        List<ArquivoBruto> alvos = resolverAlvos(conjuntoId, arquivoIds);
        if (alvos.isEmpty()) {
            return new Resultado(List.of(), "Nenhum arquivo pendente neste conjunto.");
        }

        log.info("Lote do conjunto {}: {} arquivo(s) para processar.", conjuntoId, alvos.size());

        List<PorArquivo> feitos = new ArrayList<>();

        for (ArquivoBruto arquivo : alvos) {
            ContextoExecucao ctx = ContextoExecucao.novo(conjuntoId, arquivo.getId(), usuarioId);
            if (competencia != null && !competencia.isBlank()) {
                ctx.setCompetencia(competencia);
            }
            if (parametros != null) {
                parametros.forEach(ctx::comParametro);
            }

            try {
                List<ExecucaoPipeline> etapas = executor.executarAutomatico(ctx);
                feitos.add(PorArquivo.concluido(arquivo, ctx.getDagRunId(), etapas));
                log.info("Lote: '{}' concluido em {} etapa(s).", arquivo.getNome(), etapas.size());

            } catch (RuntimeException e) {
                log.error("Lote: '{}' falhou: {}", arquivo.getNome(), e.getMessage());
                feitos.add(PorArquivo.falhou(arquivo, ctx.getDagRunId(), e));

                if (pararNoPrimeiroErro) {
                    return new Resultado(feitos,
                            "Lote interrompido no arquivo '" + arquivo.getNome() + "'.");
                }
            }
        }

        return new Resultado(feitos, null);
    }

    // arquivos escolhidos, ou todos os que ainda nao foram processados
    @Transactional(readOnly = true)
    public List<ArquivoBruto> resolverAlvos(Long conjuntoId, List<Long> arquivoIds) {
        if (arquivoIds != null && !arquivoIds.isEmpty()) {
            return arquivoRepository.findAllById(arquivoIds).stream()
                    .filter(a -> a.getPai() == null)
                    .filter(a -> a.getConjunto() != null
                            && a.getConjunto().getId().equals(conjuntoId))
                    .toList();
        }

        // arquivo com FALHA volta para a fila: antes ele saia do fluxo automatico
        // para sempre e ninguem ficava sabendo. Falhando de novo, aparece em
        // GET /api/pipeline/execucoes?filtro=FALHADAS
        return arquivoRepository
                .findByConjunto_IdAndPaiIsNullOrderByDataUploadDesc(conjuntoId).stream()
                .filter(a -> !"PROCESSADO".equals(a.getStatus()))
                .toList();
    }

    public record PorArquivo(
            Long arquivoBrutoId,
            String nome,
            String dagRunId,
            String situacao,
            long recebidas,
            long aprovadas,
            long rejeitadas,
            Long versaoGerada,
            String mensagem,
            List<ExecucaoPipeline> etapas) {

        static PorArquivo concluido(ArquivoBruto arquivo, String dagRunId,
                                    List<ExecucaoPipeline> etapas) {
            ExecucaoPipeline primeira = etapas.isEmpty() ? null : etapas.get(0);
            ExecucaoPipeline ultima = etapas.isEmpty() ? null : etapas.get(etapas.size() - 1);

            Long versao = null;
            for (ExecucaoPipeline etapa : etapas) {
                if (etapa.getVersaoGerada() != null) {
                    versao = etapa.getVersaoGerada().getId();
                }
            }

            boolean comRessalva = etapas.stream()
                    .anyMatch(e -> "COM_RESSALVA".equals(e.getStatus()));

            return new PorArquivo(arquivo.getId(), arquivo.getNome(), dagRunId,
                    comRessalva ? "COM_RESSALVA" : "CONCLUIDA",
                    primeira == null ? 0 : valor(primeira.getRegistrosRecebidos()),
                    ultima == null ? 0 : valor(ultima.getRegistrosAprovados()),
                    etapas.stream().mapToLong(e -> valor(e.getRegistrosRejeitados())).sum(),
                    versao,
                    ultima == null ? null : ultima.getMensagem(),
                    etapas);
        }

        static PorArquivo falhou(ArquivoBruto arquivo, String dagRunId, RuntimeException erro) {
            return new PorArquivo(arquivo.getId(), arquivo.getNome(), dagRunId, "FALHOU",
                    0, 0, 0, null,
                    erro.getClass().getSimpleName() + ": " + erro.getMessage(), List.of());
        }

        private static long valor(Long v) {
            return v == null ? 0L : v;
        }
    }

    public record Resultado(List<PorArquivo> arquivos, String interrupcao) {

        public int concluidos() {
            return (int) arquivos.stream().filter(a -> !"FALHOU".equals(a.situacao())).count();
        }

        public int falhados() {
            return (int) arquivos.stream().filter(a -> "FALHOU".equals(a.situacao())).count();
        }

        public long recebidas() {
            return arquivos.stream().mapToLong(PorArquivo::recebidas).sum();
        }

        public long rejeitadas() {
            return arquivos.stream().mapToLong(PorArquivo::rejeitadas).sum();
        }

        public Map<String, Long> porSituacao() {
            Map<String, Long> mapa = new LinkedHashMap<>();
            for (PorArquivo a : arquivos) {
                mapa.merge(a.situacao(), 1L, Long::sum);
            }
            return mapa;
        }
    }
}
