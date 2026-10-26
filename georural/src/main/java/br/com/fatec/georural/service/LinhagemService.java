package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.linhagem.LinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemResponse.*;
import br.com.fatec.georural.dto.response.linhagem.LinhagemResultadoItemResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class LinhagemService {

    private final ResultadoIndicadorRepository resultadoRepository;
    private final ParametroRegraCalculoRepository parametroRegraRepository;
    private final ExecucaoPipelineRepository execucaoRepository;
    private final EntradaExecucaoRepository entradaRepository;
    private final TransformacaoRepository transformacaoRepository;
    private final MovimentacaoZonaRepository movimentacaoRepository;

    @Transactional(readOnly = true)
    public List<LinhagemResultadoItemResponse> listarPublicados(String indicador, String busca) {
        String sigla = (indicador == null || indicador.isBlank() || indicador.equalsIgnoreCase("TODOS"))
                ? "" : indicador.trim().toUpperCase();
        String termo = "%" + (busca == null ? "" : busca.trim().toLowerCase()) + "%";

        return resultadoRepository.buscarPublicados(sigla, termo).stream()
                .map(r -> new LinhagemResultadoItemResponse(
                        r.getId(),
                        r.getImovel().getCodigoCar(),
                        nomeMunicipio(r.getImovel()),
                        r.getIndicador().getSigla(),
                        r.getValorPercentual(),
                        r.getValorHectares(),
                        r.getVersao().getNumero(),
                        r.getVersao().getDataPublicacao()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LinhagemResponse consultar(Long resultadoId) {
        ResultadoIndicador resultado = resultadoRepository.findById(resultadoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Resultado de indicador não encontrado: " + resultadoId));

        Versao versao = resultado.getVersao();
        // execução que calculou o resultado; se não estiver registrada, usa a que gerou a versão
        ExecucaoPipeline execucao = resultado.getExecucao() != null
                ? resultado.getExecucao() : versao.getExecucao();
        List<EntradaExecucao> entradas = execucao != null
                ? entradaRepository.findByExecucao_Id(execucao.getId()) : List.of();

        return new LinhagemResponse(
                montarResultado(resultado),
                montarRegra(resultado.getRegraCalculo()),
                montarExecucao(execucao),
                montarTransformacoes(versao, execucao, entradas),
                montarOrigem(resultado.getImovel(), entradas));
    }

    private ResultadoInfo montarResultado(ResultadoIndicador r) {
        ImovelRural imovel = r.getImovel();
        Versao versao = r.getVersao();
        return new ResultadoInfo(
                r.getId(), imovel.getCodigoCar(), nomeMunicipio(imovel), imovel.getAreaTotal(),
                r.getIndicador().getSigla(), r.getIndicador().getNome(),
                r.getValorPercentual(), r.getValorHectares(), r.getValorAbsoluto(),
                r.getSituacao(), versao.getNumero(), versao.getSituacao(),
                r.getDataCalculo(), versao.getDataPublicacao(),
                versao.getUsuario() != null ? versao.getUsuario().getNome() : null);
    }

    private RegraInfo montarRegra(RegraCalculo regra) {
        if (regra == null) return null;
        List<ParametroInfo> parametros = parametroRegraRepository.findByRegraCalculo(regra).stream()
                .map(p -> new ParametroInfo(p.getNome(), p.getValor(), p.getUnidade()))
                .toList();
        return new RegraInfo(regra.getId(), regra.getVersao(), regra.getDescricao(), regra.getAutor(),
                regra.getMotivo(), "S".equals(regra.getVigente()), regra.getDataCriacao(), parametros);
    }

    private ExecucaoInfo montarExecucao(ExecucaoPipeline e) {
        if (e == null) return null;
        // cada etapa do pipeline é uma execução; as do mesmo DAG run formam a lista de etapas
        List<String> etapas = e.getDagRunId() != null
                ? execucaoRepository.findByDagRunIdOrderByInicioAsc(e.getDagRunId()).stream()
                .map(ExecucaoPipeline::getEtapa).distinct().toList()
                : List.of(e.getEtapa());
        return new ExecucaoInfo(e.getId(), e.getEtapa(), e.getStatus(), e.getDagRunId(),
                e.getUsuario() != null ? e.getUsuario().getNome() : null,
                e.getInicio(), e.getFim(), e.getDuracaoSeg(),
                e.getRegistrosProcessados(), e.getRegistrosRejeitados(), etapas);
    }

    // tratamento do dado de origem primeiro, cálculo por último
    private List<TransformacaoInfo> montarTransformacoes(Versao versao, ExecucaoPipeline execucao,
                                                         List<EntradaExecucao> entradas) {
        Set<Long> execucaoIds = new LinkedHashSet<>();
        if (versao.getExecucao() != null) execucaoIds.add(versao.getExecucao().getId());
        for (EntradaExecucao entrada : entradas) {
            if (entrada.getVersao().getExecucao() != null) {
                execucaoIds.add(entrada.getVersao().getExecucao().getId());
            }
        }
        if (execucao != null) execucaoIds.add(execucao.getId());

        return execucaoIds.stream()
                .flatMap(id -> transformacaoRepository.findByExecucao_IdOrderByOrdemAsc(id).stream())
                .map(t -> new TransformacaoInfo(t.getOrdem(), t.getTipo(), t.getDescricao(), t.getData()))
                .toList();
    }

    private List<OrigemInfo> montarOrigem(ImovelRural imovel, List<EntradaExecucao> entradas) {
        Map<Long, ArquivoBruto> arquivos = new LinkedHashMap<>();
        if (imovel.getArquivoBruto() != null) {
            arquivos.put(imovel.getArquivoBruto().getId(), imovel.getArquivoBruto());
        }
        for (EntradaExecucao entrada : entradas) {
            ExecucaoPipeline origem = entrada.getVersao().getExecucao();
            if (origem == null) continue;
            movimentacaoRepository.findByExecucao_Id(origem.getId())
                    .forEach(m -> arquivos.putIfAbsent(m.getArquivoBruto().getId(), m.getArquivoBruto()));
        }
        return arquivos.values().stream().map(this::paraOrigem).toList();
    }

    private OrigemInfo paraOrigem(ArquivoBruto a) {
        ConjuntoDados conjunto = a.getConjunto();
        return new OrigemInfo(a.getId(), a.getNome(),
                conjunto.getFormaEntrada(),
                conjunto.getFonte() != null ? conjunto.getFonte().getNome() : null,
                a.getUsuario() != null ? a.getUsuario().getNome() : null,
                a.getDataUpload(), a.getFormato(), a.getTamanhoBytes(), a.getHash());
    }

    private String nomeMunicipio(ImovelRural imovel) {
        return imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null;
    }
}
