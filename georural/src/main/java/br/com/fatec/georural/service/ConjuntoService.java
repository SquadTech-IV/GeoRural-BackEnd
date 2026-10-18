package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.cadastro.ConjuntoRequest;
import br.com.fatec.georural.dto.response.cadastro.ConjuntoResponse;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ConjuntoService {

    private final ConjuntoDadosRepository repository;
    private final FonteService fonteService;

    public ConjuntoService(
        ConjuntoDadosRepository repository,
        FonteService fonteService
    ) {
        this.repository = repository;
        this.fonteService = fonteService;
    }

    public List<ConjuntoResponse> listar(Long fonteId) {
        List<ConjuntoDados> conjuntos;

        if (fonteId == null) {
            conjuntos = repository.findAll(Sort.by("nome"));
        } else {
            fonteService.obter(fonteId);

            conjuntos = repository.findByFonteIdOrderByNomeAsc(
                fonteId
            );
        }

        return conjuntos.stream()
            .map(this::resposta)
            .toList();
    }

    public ConjuntoResponse detalhar(Long id) {
        return resposta(obter(id));
    }

    private ConjuntoDados obter(Long id) {
        CadastroCampos.validarId(id, "o conjunto");

        return repository.findById(id)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException(
                    "Conjunto não encontrado: " + id
                )
            );
    }

    @Transactional
    public ConjuntoResponse criar(ConjuntoRequest request) {
        ConjuntoDados conjunto = new ConjuntoDados();

        conjunto.setSituacao("RASCUNHO");
        conjunto.setDataCadastro(LocalDateTime.now());

        preencher(conjunto, request);

        return resposta(repository.saveAndFlush(conjunto));
    }

    @Transactional
    public ConjuntoResponse atualizar(
        Long id,
        ConjuntoRequest request
    ) {
        ConjuntoDados conjunto = obter(id);

        preencher(conjunto, request);

        return resposta(repository.saveAndFlush(conjunto));
    }

    @Transactional
    public void desativar(Long id) {
        ConjuntoDados conjunto = obter(id);

        conjunto.setSituacao("ARQUIVADO");
    }

    private void preencher(
        ConjuntoDados conjunto,
        ConjuntoRequest request
    ) {
        if (request == null) {
            throw new RequisicaoInvalidaException(
                "Informe os dados do conjunto."
            );
        }

        CadastroCampos.validarId(
            request.fonteId(),
            "a fonte do conjunto"
        );

        FonteDoDado fonte = fonteService.obter(
            request.fonteId()
        );

        if (!"S".equals(fonte.getStatus())) {
            throw new ConflitoException(
                "Não é possível cadastrar ou editar conjuntos " +
                "de uma fonte desativada."
            );
        }

        String nome = CadastroCampos.obrigatorio(
            request.nome(),
            "nome do conjunto",
            160
        );

        String tipoCamada = CadastroCampos.obrigatorio(
            request.tipoCamada(),
            "tipo de camada",
            30
        ).toUpperCase(Locale.ROOT);

        String descricao = CadastroCampos.opcional(
            request.descricao(),
            "descrição",
            400
        );

        String esquemaTecnico = CadastroCampos.obrigatorio(
            request.esquemaTecnico(),
            "esquema técnico",
            20000
        );

        String competencia = CadastroCampos.obrigatorio(
            request.competencia(),
            "competência",
            40
        );

        String cobertura = CadastroCampos.obrigatorio(
            request.cobertura(),
            "cobertura",
            30
        );

        String periodicidade = CadastroCampos.opcional(
            request.periodicidade(),
            "periodicidade",
            20
        );

        Integer srs = request.srs();

        if (srs == null || srs <= 0 || srs > 999999) {
            throw new RequisicaoInvalidaException(
                "Informe um código EPSG entre 1 e 999999."
            );
        }

        if (conjunto.getId() != null) {
            boolean mudouFonte = !Objects.equals(
                conjunto.getFonte().getId(),
                fonte.getId()
            );

            boolean mudouTipo = !Objects.equals(
                conjunto.getTipoCamada(),
                tipoCamada
            );

            if (mudouFonte || mudouTipo) {
                throw new ConflitoException(
                    "A fonte e o tipo de camada não podem ser alterados. " +
                    "Cadastre outro conjunto para preservar a origem."
                );
            }
        }

        repository.findByFonteIdAndNomeIgnoreCase(
                fonte.getId(),
                nome
            )
            .filter(existente ->
                !Objects.equals(
                    existente.getId(),
                    conjunto.getId()
                )
            )
            .ifPresent(existente -> {
                throw new ConflitoException(
                    "Já existe um conjunto com esse nome nessa fonte."
                );
            });

        conjunto.setFonte(fonte);
        conjunto.setNome(nome);
        conjunto.setTipoCamada(tipoCamada);
        conjunto.setDescricao(descricao);
        conjunto.setEsquemaTecnico(esquemaTecnico);
        conjunto.setSrs(srs);
        conjunto.setCompetencia(competencia);
        conjunto.setCobertura(cobertura);
        conjunto.setPeriodicidade(
            periodicidade == null
                ? null
                : periodicidade.toUpperCase(Locale.ROOT)
        );
    }

    private ConjuntoResponse resposta(ConjuntoDados conjunto) {
        FonteDoDado fonte = conjunto.getFonte();

        boolean ativo =
            !"ARQUIVADO".equals(conjunto.getSituacao()) &&
            "S".equals(fonte.getStatus());

        return new ConjuntoResponse(
            conjunto.getId(),
            fonte.getId(),
            fonte.getNome(),
            conjunto.getNome(),
            conjunto.getTipoCamada(),
            conjunto.getDescricao(),
            conjunto.getEsquemaTecnico(),
            conjunto.getSrs(),
            conjunto.getCompetencia(),
            conjunto.getCobertura(),
            conjunto.getPeriodicidade(),
            conjunto.getSituacao(),
            ativo,
            conjunto.getDataCadastro()
        );
    }
}