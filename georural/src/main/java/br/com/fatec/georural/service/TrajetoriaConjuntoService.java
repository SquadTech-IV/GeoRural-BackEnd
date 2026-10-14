package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.trajetoria.StatusAtualResponse;
import br.com.fatec.georural.dto.response.trajetoria.TrajetoriaEvento;
import br.com.fatec.georural.dto.response.trajetoria.TrajetoriaResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.*;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TrajetoriaConjuntoService {
    private final ConjuntoDadosRepository conjuntoRepository;
    private final ArquivoBrutoRepository arquivoBrutoRepository;
    private final ExecucaoPipelineRepository execucaoRepository;
    private final ValidacaoRepository validacaoRepository;
    private final QuarentenaRepository quarentenaRepository;
    private final TransformacaoRepository transformacaoRepository;

    public TrajetoriaConjuntoService(ConjuntoDadosRepository conjuntoRepository,
                                     ArquivoBrutoRepository arquivoBrutoRepository,
                                     ExecucaoPipelineRepository execucaoRepository,
                                     ValidacaoRepository validacaoRepository,
                                     QuarentenaRepository quarentenaRepository,
                                     TransformacaoRepository transformacaoRepository) {
        this.conjuntoRepository = conjuntoRepository;
        this.arquivoBrutoRepository = arquivoBrutoRepository;
        this.execucaoRepository = execucaoRepository;
        this.validacaoRepository = validacaoRepository;
        this.quarentenaRepository = quarentenaRepository;
        this.transformacaoRepository = transformacaoRepository;
    }

    public TrajetoriaResponse consultarTrajetoria(Long conjuntoId) {
        ConjuntoDados conjunto = buscarConjunto(conjuntoId);
        List<TrajetoriaEvento> eventos = new ArrayList<>();

        // Zona BRUTA: upload de cada arquivo
        for (ArquivoBruto arquivo : arquivoBrutoRepository.findByConjunto_IdOrderByDataUploadAsc(conjuntoId)) {
            eventos.add(new TrajetoriaEvento(
                    "BRUTA", "UPLOAD_ARQUIVO", arquivo.getDataUpload(),
                    arquivo.getStatus(),
                    "Arquivo '" + arquivo.getNome() + "' recebido"));
        }

        // Para cada execução do pipeline desse conjunto
        for (ExecucaoPipeline execucao : execucaoRepository.findByConjunto_IdOrderByInicioAsc(conjuntoId)) {

            eventos.add(new TrajetoriaEvento(
                    "BRUTA", "EXECUCAO_INICIADA", execucao.getInicio(),
                    execucao.getStatus(),
                    "Execução da etapa '" + execucao.getEtapa() + "' iniciada"));

            // Validações dessa execução -> registros em quarentena
            for (Validacao validacao : validacaoRepository.findByExecucao_Id(execucao.getId())) {
                for (Quarentena quarentena : quarentenaRepository.findByValidacao_Id(validacao.getId())) {
                    eventos.add(new TrajetoriaEvento(
                            "QUARENTENA", "REGISTRO_REJEITADO", quarentena.getDataRegistro(),
                            quarentena.getSituacao(),
                            quarentena.getMotivoErro()));
                }
            }

            // Transformações -> zona tratada
            for (Transformacao transformacao : transformacaoRepository.findByExecucao_IdOrderByOrdemAsc(execucao.getId())) {
                eventos.add(new TrajetoriaEvento(
                        "TRATADA", "TRANSFORMACAO", transformacao.getData(),
                        null,
                        "[" + transformacao.getOrdem() + "] " + transformacao.getTipo() + ": " + transformacao.getDescricao()));
            }

            if (execucao.getFim() != null) {
                eventos.add(new TrajetoriaEvento(
                        "TRATADA", "EXECUCAO_CONCLUIDA", execucao.getFim(),
                        execucao.getStatus(),
                        "Execução concluída (" + execucao.getRegistrosProcessados() + " processados, "
                                + execucao.getRegistrosRejeitados() + " rejeitados)"));
            }
        }

        eventos.sort(Comparator.comparing(TrajetoriaEvento::dataHora));

        String zonaAtual = eventos.isEmpty() ? "BRUTA" : eventos.get(eventos.size() - 1).zona();

        return new TrajetoriaResponse(conjunto.getId(), conjunto.getNome(), zonaAtual, eventos);
    }

    public StatusAtualResponse consultarStatusAtual(Long conjuntoId) {
        TrajetoriaResponse trajetoria = consultarTrajetoria(conjuntoId);
        ConjuntoDados conjunto = buscarConjunto(conjuntoId);

        var ultimoEvento = trajetoria.eventos().isEmpty() ? null
                : trajetoria.eventos().get(trajetoria.eventos().size() - 1);

        return new StatusAtualResponse(
                conjunto.getId(),
                conjunto.getNome(),
                trajetoria.zonaAtual(),
                conjunto.getSituacao(),
                ultimoEvento == null ? conjunto.getDataCadastro() : ultimoEvento.dataHora());
    }

    private ConjuntoDados buscarConjunto(Long id) {
        return conjuntoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Conjunto de dados com id " + id + " não encontrado."));
    }
}
