package br.com.fatec.georural.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fatec.georural.dto.response.tratada.ProcessamentoTratadaResponse;
import br.com.fatec.georural.dto.response.tratada.TransformacaoTratadaResponse;
import br.com.fatec.georural.dto.response.tratada.ZonaTratadaResumoResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.entity.Transformacao;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import br.com.fatec.georural.repository.TransformacaoRepository;

/**
 * Alimenta a tela "Zona tratada" (US09) com dados REAIS do pipeline:
 *  - lista as execuções que gravaram na zona tratada (MOVIMENTACAO_ZONA zona=TRATADA);
 *  - para cada execução, os passos de tratamento (TRANSFORMACAO) com rótulo amigável e
 *    o efeito (entrada -> saída) extraído da descrição;
 *  - as informações da execução (EXECUCAO_PIPELINE).
 *
 * O nome lógico de cada passo vem do prefixo [X] da descrição (quando houver) ou do tipo
 * gravado; só os passos de TRATAMENTO entram (validação/quarentena ficam na tela US08).
 */
@Service
public class ConsultaZonaTratadaService {

    private static final String ZONA_TRATADA = "TRATADA";

    // Nomes lógicos que representam passos de TRATAMENTO (os demais — carga de validação,
    // checagens de campos/duplicidade — pertencem à tela de quarentena, não a esta).
    private static final Set<String> PASSOS_TRATAMENTO = Set.of(
            "CARGA", "REPROJECAO", "CRUZAMENTO", "RECORTE", "DISSOLVER", "DISSOLVE",
            "PADRONIZACAO", "CALCULO_AREA", "CALCULO", "AGREGACAO", "GRAVACAO_ZONA_TRATADA");

    private static final Pattern PREFIXO = Pattern.compile("^\\s*\\[([^\\]]+)\\]");
    private static final Pattern ENTRADA =
            Pattern.compile("entrada\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SAIDA =
            Pattern.compile("sa[ií]da\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final MovimentacaoZonaRepository movimentacaoRepository;
    private final TransformacaoRepository transformacaoRepository;
    private final ExecucaoPipelineRepository execucaoRepository;

    public ConsultaZonaTratadaService(MovimentacaoZonaRepository movimentacaoRepository,
                                      TransformacaoRepository transformacaoRepository,
                                      ExecucaoPipelineRepository execucaoRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
        this.transformacaoRepository = transformacaoRepository;
        this.execucaoRepository = execucaoRepository;
    }

    /**
     * Uma linha por CONJUNTO que passou pela zona tratada — a zona tratada guarda dados por
     * conjunto, não arquivos nem execuções. Usa o tratamento mais recente de cada conjunto
     * (a lista vem ordenada por data desc, então a primeira ocorrência de cada conjunto já é a
     * mais recente). Reprocessamentos do mesmo conjunto não viram linhas repetidas.
     */
    @Transactional(readOnly = true)
    public List<ZonaTratadaResumoResponse> listar() {
        List<ZonaTratadaResumoResponse> resultado = new ArrayList<>();
        Set<Long> conjuntosVistos = new java.util.HashSet<>();

        for (MovimentacaoZona mov : movimentacaoRepository.findByZonaOrderByDataDesc(ZONA_TRATADA)) {
            ExecucaoPipeline execucao = mov.getExecucao();
            ArquivoBruto arquivo = mov.getArquivoBruto();
            if (execucao == null || arquivo == null) {
                continue;
            }

            ConjuntoDados conjunto = arquivo.getConjunto();
            if (conjunto == null || conjunto.getId() == null) {
                continue; // sem conjunto não há dado tratado a listar
            }
            if (!conjuntosVistos.add(conjunto.getId())) {
                continue; // já incluímos o tratamento mais recente deste conjunto
            }

            Long aprovados = execucao.getRegistrosAprovados();
            Long tratada = contarTratada(execucao.getId(), aprovados);

            resultado.add(new ZonaTratadaResumoResponse(
                    conjunto.getId(),
                    execucao.getId(),
                    arquivo.getId(),
                    conjunto.getNome(),
                    conjunto.getFonte() != null ? conjunto.getFonte().getNome() : null,
                    conjunto.getTipoCamada(),
                    conjunto.getSituacao() != null ? conjunto.getSituacao() : execucao.getStatus(),
                    null,
                    aprovados,
                    tratada,
                    mov.getData() != null ? mov.getData() : execucao.getFim()));
        }

        return resultado;
    }

    /** Passos de tratamento de uma execução, em ordem, com rótulo e efeito legíveis. */
    @Transactional(readOnly = true)
    public List<TransformacaoTratadaResponse> transformacoes(Long execucaoId) {
        List<TransformacaoTratadaResponse> passos = new ArrayList<>();

        for (Transformacao t : transformacaoRepository.findByExecucao_IdOrderByOrdemAsc(execucaoId)) {
            String logico = logico(t);
            if (!PASSOS_TRATAMENTO.contains(logico)) {
                continue; // oculta carga de validação/quarentena nesta tela
            }

            long[] es = parseEntradaSaida(t.getDescricao());
            Long entrada = es != null ? es[0] : null;
            Long saida = es != null ? es[1] : null;
            String efeito = efeito(entrada, saida);

            passos.add(new TransformacaoTratadaResponse(
                    t.getId(),
                    t.getOrdem(),
                    rotulo(logico),
                    descricaoHumana(logico),
                    efeito != null ? efeito : "concluída",
                    t.getData(),
                    entrada,
                    saida));
        }

        return passos;
    }

    /** Informações da execução que tratou o dado. */
    @Transactional(readOnly = true)
    public ProcessamentoTratadaResponse processamento(Long execucaoId) {
        ExecucaoPipeline e = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Execução não encontrada: " + execucaoId));

        Long versaoNumero = e.getVersaoGerada() != null ? e.getVersaoGerada().getNumero() : null;

        return new ProcessamentoTratadaResponse(
                e.getId(),
                e.getStatus(),
                e.getEtapa(),
                e.getInicio(),
                e.getFim(),
                e.getDuracaoSeg(),
                e.getUsuario() != null ? e.getUsuario().getNome() : null,
                e.getRegistrosRecebidos(),
                e.getRegistrosProcessados(),
                e.getRegistrosAprovados(),
                e.getRegistrosRejeitados(),
                versaoNumero);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Quantos registros efetivamente ficaram na zona tratada (saída da gravação, senão último passo). */
    private Long contarTratada(Long execucaoId, Long fallback) {
        Long ultimaSaida = null;
        for (Transformacao t : transformacaoRepository.findByExecucao_IdOrderByOrdemAsc(execucaoId)) {
            long[] es = parseEntradaSaida(t.getDescricao());
            if (es == null) {
                continue;
            }
            if ("GRAVACAO_ZONA_TRATADA".equals(logico(t))) {
                return es[1];
            }
            ultimaSaida = es[1];
        }
        return ultimaSaida != null ? ultimaSaida : fallback;
    }

    /** Nome lógico: do prefixo [X] da descrição, ou do tipo gravado. */
    private String logico(Transformacao t) {
        String d = t.getDescricao();
        if (d != null) {
            Matcher m = PREFIXO.matcher(d);
            if (m.find()) {
                return m.group(1).trim().toUpperCase();
            }
        }
        return t.getTipo() != null ? t.getTipo().trim().toUpperCase() : "";
    }

    /** Extrai [entrada, saída] da descrição; null se não houver números. */
    private long[] parseEntradaSaida(String descricao) {
        if (descricao == null) {
            return null;
        }
        Matcher me = ENTRADA.matcher(descricao);
        Matcher ms = SAIDA.matcher(descricao);
        boolean temEntrada = me.find();
        boolean temSaida = ms.find();
        if (!temEntrada && !temSaida) {
            return null;
        }
        long entrada = temEntrada ? Long.parseLong(me.group(1)) : -1;
        long saida = temSaida ? Long.parseLong(ms.group(1)) : entrada;
        if (entrada < 0) {
            entrada = saida;
        }
        return new long[]{entrada, saida};
    }

    private String efeito(Long entrada, Long saida) {
        if (entrada == null || saida == null) {
            return null;
        }
        if (saida < entrada) {
            long removidos = entrada - saida;
            return removidos + (removidos == 1 ? " removido" : " removidos")
                    + " · " + entrada + " → " + saida;
        }
        return saida + (saida == 1 ? " registro" : " registros");
    }

    private String rotulo(String logico) {
        return switch (logico) {
            case "CARGA" -> "Carga do arquivo";
            case "REPROJECAO" -> "Reprojeção (SIRGAS 2000)";
            case "CRUZAMENTO", "RECORTE" -> "Recorte pelo limite do Paraná";
            case "DISSOLVER", "DISSOLVE" -> "Dissolve";
            case "PADRONIZACAO" -> "Padronização de atributos";
            case "CALCULO_AREA", "CALCULO" -> "Medição de área";
            case "GRAVACAO_ZONA_TRATADA" -> "Gravação na zona tratada";
            case "AGREGACAO" -> "Agregação";
            default -> "Transformação";
        };
    }

    private String descricaoHumana(String logico) {
        return switch (logico) {
            case "CARGA" -> "Leitura das feições do arquivo recebido na zona bruta.";
            case "REPROJECAO" -> "Geometrias reprojetadas para SIRGAS 2000 (EPSG:4674).";
            case "CRUZAMENTO", "RECORTE" -> "Feições fora do limite oficial do Paraná removidas.";
            case "DISSOLVER", "DISSOLVE" -> "Geometrias do mesmo registro unidas em uma única feição.";
            case "PADRONIZACAO" -> "Atributos normalizados para o padrão do catálogo.";
            case "CALCULO_AREA", "CALCULO" -> "Área recalculada em projeção de área equivalente.";
            case "GRAVACAO_ZONA_TRATADA" -> "Dados limpos gravados na zona tratada.";
            case "AGREGACAO" -> "Registros agregados.";
            default -> null;
        };
    }
}
