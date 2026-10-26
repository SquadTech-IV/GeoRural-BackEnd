package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IndicadorResponse;
import br.com.fatec.georural.dto.response.trajetoria.*;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.*;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TrajetoriaConjuntoService {

    private static final List<String> ORDEM_ZONAS = List.of("BRUTA", "QUARENTENA", "TRATADA", "PUBLICADA");

    private final ConjuntoDadosRepository conjuntoRepository;
    private final ArquivoBrutoRepository arquivoBrutoRepository;
    private final MovimentacaoZonaRepository movimentacaoZonaRepository;
    private final ValidacaoRepository validacaoRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;

    public TrajetoriaConjuntoService(ConjuntoDadosRepository conjuntoRepository,
                                     ArquivoBrutoRepository arquivoBrutoRepository,
                                     MovimentacaoZonaRepository movimentacaoZonaRepository,
                                     ValidacaoRepository validacaoRepository,
                                     ResultadoIndicadorRepository resultadoIndicadorRepository) {
        this.conjuntoRepository = conjuntoRepository;
        this.arquivoBrutoRepository = arquivoBrutoRepository;
        this.movimentacaoZonaRepository = movimentacaoZonaRepository;
        this.validacaoRepository = validacaoRepository;
        this.resultadoIndicadorRepository = resultadoIndicadorRepository;
    }

    public List<ConjuntoResumoResponse> listarConjuntos() {
        return conjuntoRepository.findAll().stream()
                .map(c -> new ConjuntoResumoResponse(c.getId(), montarRotulo(c)))
                .toList();
    }

    public ConjuntoTrajetoriaResponse consultarTrajetoriaPorArquivo(Long conjuntoId) {
        ConjuntoDados conjunto = buscarConjunto(conjuntoId);

        List<ArquivoTrajetoriaResponse> arquivos = arquivoBrutoRepository
                .findByConjunto_IdOrderByDataUploadAsc(conjuntoId)
                .stream()
                .map(this::montarTrajetoriaDoArquivo)
                .toList();

        return new ConjuntoTrajetoriaResponse(conjunto.getId(), conjunto.getNome(), arquivos);
    }

    private ArquivoTrajetoriaResponse montarTrajetoriaDoArquivo(ArquivoBruto arquivo) {
        List<MovimentacaoZona> movimentacoes =
                movimentacaoZonaRepository.findByArquivoBrutoIdOrderByDataAsc(arquivo.getId());

        Map<String, MovimentacaoZona> movsPorZona = new HashMap<>();
        for (MovimentacaoZona mov : movimentacoes) {
            movsPorZona.put(mov.getZona(), mov); // mantém a mais recente de cada zona
        }

        Validacao validacao = validacaoRepository
                .findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(arquivo.getId())
                .orElse(null);

        int indiceMaisAvancado = -1;
        for (int i = 0; i < ORDEM_ZONAS.size(); i++) {
            if (movsPorZona.containsKey(ORDEM_ZONAS.get(i))) {
                indiceMaisAvancado = i;
            }
        }

        List<PassagemZonaResponse> passagens = new ArrayList<>();
        for (int i = 0; i < ORDEM_ZONAS.size(); i++) {
            String zona = ORDEM_ZONAS.get(i);
            MovimentacaoZona mov = movsPorZona.get(zona);

            if (mov == null) {
                passagens.add(new PassagemZonaResponse(zona, "pendente", null, null, null));
                continue;
            }

            String status;
            if (i == indiceMaisAvancado) {
                status = zona.equals("PUBLICADA") ? "done" : "atual";
            } else if (zona.equals("QUARENTENA") && temRessalva(validacao)) {
                status = "ressalva";
            } else {
                status = "done";
            }

            passagens.add(new PassagemZonaResponse(
                    zona, status, mov.getData(), montarContagem(zona, validacao), mov.getObservacao()));
        }

        String zonaAtual = indiceMaisAvancado >= 0 ? ORDEM_ZONAS.get(indiceMaisAvancado) : "BRUTA";

        return new ArquivoTrajetoriaResponse(
                arquivo.getId(), arquivo.getNome(),
                arquivo.getConjunto().getFonte() != null ? arquivo.getConjunto().getFonte().getNome() : null,
                arquivo.getDataUpload(), zonaAtual, passagens,
                buscarIndicadoresGerados(movimentacoes));
    }

    // Indicadores gerados a partir do arquivo: movimentação -> execução -> resultado -> indicador
    private List<IndicadorResponse> buscarIndicadoresGerados(List<MovimentacaoZona> movimentacoes) {
        Set<Long> execucaoIds = new HashSet<>();
        for (MovimentacaoZona mov : movimentacoes) {
            if (mov.getExecucao() != null) {
                execucaoIds.add(mov.getExecucao().getId());
            }
        }
        if (execucaoIds.isEmpty()) {
            return List.of();
        }
        return resultadoIndicadorRepository.buscarIndicadoresGeradosPorExecucoes(execucaoIds)
                .stream()
                .map(IndicadorResponse::de)
                .toList();
    }

    private boolean temRessalva(Validacao validacao) {
        if (validacao == null || validacao.getRegistrosInvalidos() == null || validacao.getTotalRegistros() == null) {
            return false;
        }
        return validacao.getRegistrosInvalidos() > 0
                && validacao.getRegistrosInvalidos() < validacao.getTotalRegistros();
    }

    private String montarContagem(String zona, Validacao validacao) {
        if (validacao == null) return null;
        return switch (zona) {
            case "BRUTA" -> validacao.getTotalRegistros() + " registros recebidos";
            case "QUARENTENA" -> (validacao.getRegistrosInvalidos() != null && validacao.getRegistrosInvalidos() > 0)
                    ? validacao.getRegistrosInvalidos() + " retidos · " + validacao.getRegistrosValidos() + " seguiram"
                    : "sem registros em quarentena";
            default -> null; // TRATADA/PUBLICADA: sem fonte de contagem confirmada ainda
        };
    }

    private String montarRotulo(ConjuntoDados conjunto) {
        return conjunto.getFonte() != null
                ? conjunto.getFonte().getNome() + " · " + conjunto.getNome()
                : conjunto.getNome();
    }

    private ConjuntoDados buscarConjunto(Long id) {
        return conjuntoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Conjunto de dados com id " + id + " não encontrado."));
    }
}