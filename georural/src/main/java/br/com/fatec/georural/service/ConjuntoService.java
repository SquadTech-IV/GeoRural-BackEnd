package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.cadastro.ConjuntoRequest;
import br.com.fatec.georural.dto.response.cadastro.ConjuntoResponse;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.entity.TipoConjunto;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ConjuntoService {
    private final ConjuntoDadosRepository repository;
    private final FonteService fonteService;
    private final CadastroValidacaoService validacao;
    private final CatalogoConjuntoService catalogo;
    private final AutenticacaoApiService autenticacaoApi;

    public ConjuntoService(ConjuntoDadosRepository repository, FonteService fonteService,
            CadastroValidacaoService validacao, CatalogoConjuntoService catalogo,
            AutenticacaoApiService autenticacaoApi) {
        this.repository = repository;
        this.fonteService = fonteService;
        this.validacao = validacao;
        this.catalogo = catalogo;
        this.autenticacaoApi = autenticacaoApi;
    }

    public List<ConjuntoResponse> listar(Long fonteId) {
        List<ConjuntoDados> dados;
        if (fonteId == null) dados = repository.findAll(Sort.by("nome"));
        else {
            fonteService.obter(fonteId);
            dados = repository.findByFonteIdOrderByNomeAsc(fonteId);
        }
        return dados.stream().map(this::resposta).toList();
    }

    public ConjuntoResponse detalhar(Long id) {
        return resposta(obter(id));
    }

    private ConjuntoDados obter(Long id) {
        CadastroCampos.validarId(id, "o conjunto");
        return repository.findById(id).orElseThrow(() ->
            new RecursoNaoEncontradoException("Conjunto não encontrado: " + id));
    }

    @Transactional
    public ConjuntoResponse criar(ConjuntoRequest request) {
        ConjuntoDados conjunto = new ConjuntoDados();
        preencher(conjunto, request);
        return resposta(repository.saveAndFlush(conjunto));
    }

    @Transactional
    public ConjuntoResponse atualizar(Long id, ConjuntoRequest request) {
        ConjuntoDados conjunto = obter(id);
        preencher(conjunto, request);
        return resposta(repository.saveAndFlush(conjunto));
    }

    @Transactional
    public void desativar(Long id) {
        obter(id).setSituacao("ARQUIVADO");
    }

    @Transactional
    public ConjuntoResponse reativar(Long id) {
        ConjuntoDados conjunto = obter(id);
        if (!"S".equals(conjunto.getFonte().getStatus())) {
            throw new ConflitoException("Reative a fonte antes de reativar o conjunto.");
        }
        if ("ARQUIVADO".equals(conjunto.getSituacao())) conjunto.setSituacao("RASCUNHO");
        return resposta(conjunto);
    }

    private void preencher(ConjuntoDados conjunto, ConjuntoRequest request) {
        TipoConjunto tipo = catalogo.resolver(request, conjunto);
        boolean upload = request.upload() == null ? conjunto.permiteUpload() : request.upload();
        boolean api = request.api() == null ? conjunto.permiteApi() : request.api();
        String endpoint = api ? request.endpoint() == null ? conjunto.getEndpoint() : request.endpoint() : null;
        String formato = api ? request.formatoApi() == null ? conjunto.getFormatoApi() : request.formatoApi() : null;
        String competencia = CadastroCampos.opcional(request.competencia(), "competência", 20);
        if (competencia != null && !Objects.equals(competencia, conjunto.getCompetencia())) {
            throw new RequisicaoInvalidaException("A competência pertence à versão e deve ser informada no fluxo de versionamento.");
        }
        ConjuntoRequest normalizado = new ConjuntoRequest(
            request.fonteId(), request.nome(), tipo.getCodigo(), request.descricao(), tipo.getEsquema(),
            request.srs(), conjunto.getCompetencia(), request.cobertura(), request.periodicidade(),
            upload, api, endpoint, formato, request.autenticacao());
        validacao.validarConjunto(normalizado);
        validacao.validarEntradaConjunto(normalizado);

        FonteDoDado fonte = fonteService.obter(request.fonteId());
        if (!"S".equals(fonte.getStatus())) {
            throw new ConflitoException("Não é possível cadastrar ou editar conjuntos de uma fonte desativada.");
        }
        if (conjunto.getId() != null && (!Objects.equals(conjunto.getFonte().getId(), fonte.getId())
                || !Objects.equals(conjunto.getTipoConjunto().getId(), tipo.getId()))) {
            throw new ConflitoException("A fonte e o tipo não podem ser alterados. Cadastre outro conjunto para preservar a origem.");
        }
        repository.findByFonteIdAndTipoConjuntoId(fonte.getId(), tipo.getId())
            .filter(existente -> !Objects.equals(existente.getId(), conjunto.getId()))
            .ifPresent(existente -> { throw new ConflitoException("Essa fonte já possui um conjunto desse tipo."); });
        String nome = request.nome().trim();
        repository.findByFonteIdAndNomeIgnoreCase(fonte.getId(), nome)
            .filter(existente -> !Objects.equals(existente.getId(), conjunto.getId()))
            .ifPresent(existente -> { throw new ConflitoException("Já existe um conjunto com esse nome nessa fonte."); });

        conjunto.setFonte(fonte);
        conjunto.setTipoConjunto(tipo);
        conjunto.setNome(nome);
        conjunto.setDescricao(CadastroCampos.opcional(request.descricao(), "descrição", 400));
        conjunto.setSrs(request.srs());
        conjunto.setCobertura(request.cobertura().trim());
        conjunto.setPeriodicidade(validacao.normalizarPeriodicidade(request.periodicidade()));
        conjunto.setFormaEntrada(upload && api ? "AMBOS" : api ? "API" : "UPLOAD");
        conjunto.setEndpoint(CadastroCampos.opcional(endpoint, "endpoint", 500));
        conjunto.setFormatoApi(formato);
        if (!api) conjunto.setAutenticacao(null);
        else if (request.autenticacao() != null && !request.autenticacao().isBlank()) {
            conjunto.setAutenticacao(autenticacaoApi.proteger(request.autenticacao()));
        }
    }

    private ConjuntoResponse resposta(ConjuntoDados conjunto) {
        FonteDoDado fonte = conjunto.getFonte();
        boolean ativo = !"ARQUIVADO".equals(conjunto.getSituacao()) && "S".equals(fonte.getStatus());
        return new ConjuntoResponse(
            conjunto.getId(), fonte.getId(), fonte.getNome(), conjunto.getNome(), conjunto.getTipoCamada(),
            conjunto.getDescricao(), conjunto.getEsquemaTecnico(), conjunto.getSrs(), conjunto.getCompetencia(),
            conjunto.getCobertura(), conjunto.getPeriodicidade(), conjunto.getSituacao(), ativo,
            conjunto.getDataCadastro(), conjunto.permiteUpload(), conjunto.permiteApi(),
            conjunto.getEndpoint(), conjunto.getFormatoApi(), conjunto.getAutenticacao() != null);
    }
}
