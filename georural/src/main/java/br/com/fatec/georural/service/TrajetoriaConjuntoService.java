package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.trajetoria.StatusAtualResponse;
import br.com.fatec.georural.dto.response.trajetoria.TrajetoriaEvento;
import br.com.fatec.georural.dto.response.trajetoria.TrajetoriaResponse;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrajetoriaConjuntoService {

    private final ConjuntoDadosRepository conjuntoRepository;
    private final MovimentacaoZonaRepository movimentacaoZonaRepository;

    public TrajetoriaConjuntoService(ConjuntoDadosRepository conjuntoRepository,
                                     MovimentacaoZonaRepository movimentacaoZonaRepository) {
        this.conjuntoRepository = conjuntoRepository;
        this.movimentacaoZonaRepository = movimentacaoZonaRepository;
    }

    public TrajetoriaResponse consultarTrajetoria(Long conjuntoId) {
        ConjuntoDados conjunto = buscarConjunto(conjuntoId);

        List<TrajetoriaEvento> eventos = movimentacaoZonaRepository
                .findByArquivoBruto_Conjunto_IdOrderByDataAsc(conjuntoId)
                .stream()
                .map(this::paraEvento)
                .toList();

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

    private TrajetoriaEvento paraEvento(MovimentacaoZona movimentacao) {
        String descricao = movimentacao.getObservacao() != null
                ? movimentacao.getObservacao()
                : "Arquivo '" + movimentacao.getArquivoBruto().getNome() + "' entrou na zona " + movimentacao.getZona();

        return new TrajetoriaEvento(
                movimentacao.getZona(),
                "MOVIMENTACAO_ZONA",
                movimentacao.getData(),
                movimentacao.getStatus(),
                descricao);
    }

    private ConjuntoDados buscarConjunto(Long id) {
        return conjuntoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Conjunto de dados com id " + id + " não encontrado."));
    }
}
