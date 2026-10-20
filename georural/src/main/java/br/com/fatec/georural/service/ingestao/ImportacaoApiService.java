package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.dto.ingestao.ImportacaoResponse;
import br.com.fatec.georural.dto.ingestao.ItemDisponivelResponse;
import br.com.fatec.georural.dto.ingestao.ParametrosConsultaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import br.com.fatec.georural.dto.ingestao.ResultadoImportacaoItem;
import br.com.fatec.georural.dto.ingestao.ResultadoValidacao;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.FalhaComunicacaoApiException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImportacaoApiService {

    private final ConjuntoDadosRepository conjuntoRepository;
    private final MapeadorParametrosApi mapeador;
    private final IntegracaoApiService integracao;
    private final LeitorRespostaApi leitor;
    private final ValidadorRecebimentoApi validador;
    private final DuplicidadeService duplicidade;
    private final IngestaoZonaBrutaService ingestao;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional(readOnly = true)
    public List<ItemDisponivelResponse> listarDisponiveis(Long conjuntoId) {
        ConjuntoDados c = carregarConjuntoApi(conjuntoId);
        List<ItemDisponivelResponse> itens = new ArrayList<>();
        for (String identificador : candidatos(c)) {
            String nome = nomeDoItem(c, identificador);
            boolean ja = duplicidade.jaRecebido(c.getId(), nome);
            itens.add(new ItemDisponivelResponse(identificador, nome, descricao(c, identificador), ja));
        }
        return itens;
    }

    // ---------------- importar ----------------

    @Transactional
    public ImportacaoResponse importar(Long conjuntoId, List<String> identificadores) {
        ConjuntoDados c = carregarConjuntoApi(conjuntoId);
        if (identificadores == null || identificadores.isEmpty()) {
            throw new RequisicaoInvalidaException("Selecione ao menos um item para importar.");
        }
        Set<String> hashesNestaImportacao = new HashSet<>();
        List<ResultadoImportacaoItem> resultados = new ArrayList<>();
        for (String identificador : identificadores) {
            resultados.add(importarItem(c, identificador, hashesNestaImportacao));
        }
        return ImportacaoResponse.de(resultados);
    }

    private ResultadoImportacaoItem importarItem(ConjuntoDados c, String identificador, Set<String> hashesNestaImportacao) {
        String nome = nomeDoItem(c, identificador);

        if (duplicidade.jaRecebido(c.getId(), nome)) {
            return ResultadoImportacaoItem.jaRecebido(identificador, nome);
        }

        ParametrosConsultaApi params = mapeador.montar(c, filtrosDoItem(c, identificador));

        RespostaApi resposta;
        try {
            resposta = integracao.consultar(params);
        } catch (FalhaComunicacaoApiException e) {
            return ResultadoImportacaoItem.falha(identificador, nome, e.getMessage());
        }

        //  leitura +  validacao
        ConteudoRecebido conteudo = leitor.ler(resposta, params.formato());
        ResultadoValidacao validacao = validador.validar(resposta, conteudo, params.formato());
        if (!validacao.valido()) {
            return ResultadoImportacaoItem.falha(identificador, nome, validacao.resumo());
        }

        String hash = IngestaoZonaBrutaService.sha256(conteudo.conteudo());
        if (duplicidade.jaExistePorHash(hash) || !hashesNestaImportacao.add(hash)) {
            return ResultadoImportacaoItem.jaRecebido(identificador, nome);
        }

        // grava na zona bruta
        ArquivoBruto arquivo = ingestao.registrar(c, null, nome, params.formato(), conteudo, "API");
        return ResultadoImportacaoItem.sucesso(identificador, nome, conteudo.quantidadeRegistros(), arquivo.getId());
    }

    // ---------------- helpers ----------------

    private ConjuntoDados carregarConjuntoApi(Long conjuntoId) {
        ConjuntoDados c = conjuntoRepository.findById(conjuntoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conjunto nao encontrado: " + conjuntoId));
        String forma = c.getFormaEntrada();
        if (!"API".equals(forma) && !"AMBOS".equals(forma)) {
            throw new RequisicaoInvalidaException("O conjunto \"" + c.getNome() + "\" nao aceita entrada por API.");
        }
        if (c.getApiEndpoint() == null || c.getApiEndpoint().isBlank()) {
            throw new RequisicaoInvalidaException("O conjunto nao tem endpoint de API configurado no cadastro.");
        }
        return c;
    }

    private String nomeDoItem(ConjuntoDados c, String identificador) {
        return base(c) + "_" + identificador + "." + ext(c);
    }

    private String base(ConjuntoDados c) {
        String origem = (c.getTipo() != null && c.getTipo().getCodigo() != null) ? c.getTipo().getCodigo() : c.getNome();
        return slug(origem);
    }

    private static String slug(String s) {
        if (s == null || s.isBlank()) return "conjunto";
        String x = s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return x.isBlank() ? "conjunto" : x;
    }

    private String ext(ConjuntoDados c) {
        return FormatoRespostaApi.doCadastro(c.getApiFormato()).extensao();
    }

    private enum Cadencia { DIARIA, SEMANAL, MENSAL, ANUAL, EVENTUAL }

    private static Cadencia cadencia(ConjuntoDados c) {
        String p = (c.getPeriodicidade() == null) ? "" : c.getPeriodicidade().trim().toUpperCase();
        if (p.startsWith("DIAR") || p.startsWith("DIÁR")) return Cadencia.DIARIA;
        if (p.startsWith("SEMAN")) return Cadencia.SEMANAL;
        if (p.startsWith("MENS")) return Cadencia.MENSAL;
        if (p.startsWith("ANU")) return Cadencia.ANUAL;
        return Cadencia.EVENTUAL;
    }

    private List<String> candidatos(ConjuntoDados c) {
        LocalDate hoje = LocalDate.now();
        List<String> ids = new ArrayList<>();
        switch (cadencia(c)) {
            case DIARIA -> { for (int i = 0; i < 7; i++) ids.add(hoje.minusDays(i).format(DATA)); }
            case SEMANAL -> { for (int i = 0; i < 8; i++) ids.add(hoje.minusWeeks(i).format(DATA)); }
            case MENSAL -> { for (int i = 0; i < 6; i++) ids.add(hoje.minusMonths(i).format(MES)); }
            case ANUAL -> { int ano = hoje.getYear(); for (int i = 0; i < 3; i++) ids.add(String.valueOf(ano - i)); }
            case EVENTUAL -> ids.add(hoje.format(DATA));
        }
        return ids;
    }

    private Map<String, String> filtrosDoItem(ConjuntoDados c, String identificador) {
        return switch (cadencia(c)) {
            case ANUAL -> Map.of("ano", identificador);
            case MENSAL -> Map.of("mes", identificador);
            default -> Map.of("data", identificador);
        };
    }

    private String descricao(ConjuntoDados c, String identificador) {
        return switch (cadencia(c)) {
            case ANUAL -> "Ano " + identificador;
            case MENSAL -> "Competencia " + identificador;
            default -> "Coleta de " + identificador;
        };
    }
}