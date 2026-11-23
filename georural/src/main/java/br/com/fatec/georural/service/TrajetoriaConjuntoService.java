package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IndicadorResponse;
import br.com.fatec.georural.dto.response.trajetoria.*;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.*;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TrajetoriaConjuntoService {

    private static final List<String> ORDEM_ZONAS = List.of("BRUTA", "QUARENTENA", "TRATADA", "PUBLICADA");

    private final ConjuntoDadosRepository conjuntoRepository;
    private final ArquivoBrutoRepository arquivoBrutoRepository;
    private final MovimentacaoZonaRepository movimentacaoZonaRepository;
    private final ValidacaoRepository validacaoRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;
    private final VersaoRepository versaoRepository;

    public TrajetoriaConjuntoService(ConjuntoDadosRepository conjuntoRepository,
                                     ArquivoBrutoRepository arquivoBrutoRepository,
                                     MovimentacaoZonaRepository movimentacaoZonaRepository,
                                     ValidacaoRepository validacaoRepository,
                                     ResultadoIndicadorRepository resultadoIndicadorRepository,
                                     VersaoRepository versaoRepository) {
        this.conjuntoRepository = conjuntoRepository;
        this.arquivoBrutoRepository = arquivoBrutoRepository;
        this.movimentacaoZonaRepository = movimentacaoZonaRepository;
        this.validacaoRepository = validacaoRepository;
        this.resultadoIndicadorRepository = resultadoIndicadorRepository;
        this.versaoRepository = versaoRepository;
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

        // lista ordenada por data asc -> fica a mais recente de cada zona
        Map<String, MovimentacaoZona> movsPorZona = new HashMap<>();
        for (MovimentacaoZona mov : movimentacoes) {
            movsPorZona.put(mov.getZona(), mov);
        }

        Validacao validacao = validacaoRepository
                .findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(arquivo.getId())
                .orElse(null);

        Versao publicada = versaoRepository
                .findPublicadasPorArquivo(arquivo.getId())
                .stream().findFirst().orElse(null);

        // "Alcançou a zona?" vem da FONTE REAL de cada zona, e não só de MOVIMENTACAO_ZONA:
        //  - BRUTA: o arquivo existe em ARQUIVO_BRUTO, logo foi recebido na zona bruta (o upload
        //    não grava movimentação de BRUTA; só a ingestão por API grava).
        //  - QUARENTENA: existe uma VALIDACAO (a validação rodou) — nada grava movimentação de QUARENTENA.
        //  - TRATADA: o tratamento gravou a zona tratada (movimentação TRATADA).
        //  - PUBLICADA: o pipeline publica direto (VERSAO VIGENTE) sem gravar movimentação de PUBLICADA.
        // Sem isso a tela mostrava BRUTA/QUARENTENA/PUBLICADA sempre como "pendente".
        boolean processado = validacao != null;
        boolean tratadaOk = movsPorZona.containsKey("TRATADA");
        boolean publicadaOk = publicada != null || movsPorZona.containsKey("PUBLICADA");

        Map<String, Boolean> alcancou = new HashMap<>();
        alcancou.put("BRUTA", true);
        alcancou.put("QUARENTENA", processado);
        alcancou.put("TRATADA", tratadaOk);
        alcancou.put("PUBLICADA", publicadaOk);

        int indiceMaisAvancado = -1;
        for (int i = 0; i < ORDEM_ZONAS.size(); i++) {
            if (Boolean.TRUE.equals(alcancou.get(ORDEM_ZONAS.get(i)))) {
                indiceMaisAvancado = i;
            }
        }

        boolean houveQuarentena = temRegistrosEmQuarentena(validacao);

        List<PassagemZonaResponse> passagens = new ArrayList<>();
        for (int i = 0; i < ORDEM_ZONAS.size(); i++) {
            String zona = ORDEM_ZONAS.get(i);

            if (!Boolean.TRUE.equals(alcancou.get(zona))) {
                passagens.add(new PassagemZonaResponse(zona, "pendente", null, null, null));
                continue;
            }

            passagens.add(new PassagemZonaResponse(
                    zona,
                    statusDaZona(zona, i, indiceMaisAvancado, houveQuarentena),
                    quandoDaZona(zona, arquivo, validacao, publicada, movsPorZona),
                    montarContagem(zona, validacao, publicada),
                    observacaoDaZona(zona, validacao, movsPorZona)));
        }

        String zonaAtual = indiceMaisAvancado >= 0 ? ORDEM_ZONAS.get(indiceMaisAvancado) : "BRUTA";

        return new ArquivoTrajetoriaResponse(
                arquivo.getId(), arquivo.getNome(),
                arquivo.getConjunto().getFonte() != null ? arquivo.getConjunto().getFonte().getNome() : null,
                arquivo.getDataUpload(), zonaAtual, passagens,
                buscarIndicadoresGerados(movimentacoes));
    }

    /**
     * Status de cada zona já alcançada (as não alcançadas saem como "pendente" antes de chegar aqui).
     * QUARENTENA é um checkpoint: nunca fica "em andamento", mostra o resultado da validação
     * (ressalva se reteve algo, done caso contrário). PUBLICADA é terminal (done). BRUTA/TRATADA
     * mostram "atual" quando são a zona mais avançada em que o arquivo está, e "done" quando já
     * foram superadas por uma zona posterior.
     */
    private String statusDaZona(String zona, int indice, int indiceMaisAvancado, boolean houveQuarentena) {
        if (zona.equals("QUARENTENA")) {
            return houveQuarentena ? "ressalva" : "done";
        }
        if (zona.equals("PUBLICADA")) {
            return "done";
        }
        return indice == indiceMaisAvancado ? "atual" : "done";
    }

    private LocalDateTime quandoDaZona(String zona, ArquivoBruto arquivo, Validacao validacao,
                                       Versao publicada, Map<String, MovimentacaoZona> movsPorZona) {
        MovimentacaoZona mov = movsPorZona.get(zona);
        return switch (zona) {
            case "BRUTA" -> mov != null ? mov.getData() : arquivo.getDataUpload();
            case "QUARENTENA" -> validacao != null ? validacao.getDataExecucao()
                    : (mov != null ? mov.getData() : null);
            case "PUBLICADA" -> (publicada != null && publicada.getDataPublicacao() != null)
                    ? publicada.getDataPublicacao()
                    : (mov != null ? mov.getData() : null);
            default -> mov != null ? mov.getData() : null; // TRATADA
        };
    }

    private String montarContagem(String zona, Validacao validacao, Versao publicada) {
        return switch (zona) {
            case "BRUTA" -> (validacao != null && validacao.getTotalRegistros() != null)
                    ? validacao.getTotalRegistros() + " registros recebidos"
                    : null;
            case "QUARENTENA" -> {
                if (validacao == null) {
                    yield null;
                }
                Long invalidos = validacao.getRegistrosInvalidos();
                if (invalidos != null && invalidos > 0) {
                    long seguiram = validacao.getRegistrosValidos() != null ? validacao.getRegistrosValidos() : 0;
                    yield invalidos + " retidos · " + seguiram + " seguiram";
                }
                yield "sem registros em quarentena";
            }
            case "TRATADA" -> (validacao != null && validacao.getRegistrosValidos() != null)
                    ? validacao.getRegistrosValidos() + " tratados"
                    : null;
            case "PUBLICADA" -> publicada != null ? "versão v" + publicada.getNumero() : null;
            default -> null;
        };
    }

    private String observacaoDaZona(String zona, Validacao validacao, Map<String, MovimentacaoZona> movsPorZona) {
        MovimentacaoZona mov = movsPorZona.get(zona);
        return switch (zona) {
            case "BRUTA" -> (mov != null && mov.getObservacao() != null)
                    ? mov.getObservacao()
                    : "Arquivo recebido na zona bruta";
            case "QUARENTENA" -> {
                if (validacao == null) {
                    yield null;
                }
                yield temRegistrosEmQuarentena(validacao)
                        ? "Registros retidos na validação"
                        : "Validação sem pendências";
            }
            case "TRATADA" -> (mov != null && mov.getObservacao() != null)
                    ? mov.getObservacao()
                    : "Dados padronizados e recortados no limite do PR";
            case "PUBLICADA" -> (mov != null && mov.getObservacao() != null)
                    ? mov.getObservacao()
                    : "Publicado (imutável)";
            default -> null;
        };
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

    /** Houve algo retido na quarentena (validação com registros inválidos)? */
    private boolean temRegistrosEmQuarentena(Validacao validacao) {
        return validacao != null
                && validacao.getRegistrosInvalidos() != null
                && validacao.getRegistrosInvalidos() > 0;
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
