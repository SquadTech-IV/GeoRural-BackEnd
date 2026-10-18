package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.cadastro.FonteRequest;
import br.com.fatec.georural.dto.response.cadastro.FonteResponse;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.FonteDoDadoRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class FonteService {

    private final FonteDoDadoRepository repository;

    public FonteService(FonteDoDadoRepository repository) {
        this.repository = repository;
    }

    public List<FonteResponse> listar() {
        return repository.findAll(Sort.by("nome"))
            .stream()
            .map(this::resposta)
            .toList();
    }

    public FonteResponse detalhar(Long id) {
        return resposta(obter(id));
    }

    public FonteDoDado obter(Long id) {
        CadastroCampos.validarId(id, "a fonte");

        return repository.findById(id)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException(
                    "Fonte não encontrada: " + id
                )
            );
    }

    @Transactional
    public FonteResponse criar(FonteRequest request) {
        FonteDoDado fonte = new FonteDoDado();

        preencher(fonte, request);

        return resposta(repository.saveAndFlush(fonte));
    }

    @Transactional
    public FonteResponse atualizar(
        Long id,
        FonteRequest request
    ) {
        FonteDoDado fonte = obter(id);

        preencher(fonte, request);

        return resposta(repository.saveAndFlush(fonte));
    }

    @Transactional
    public void desativar(Long id) {
        FonteDoDado fonte = obter(id);

        fonte.setStatus("N");
    }

    @Transactional
    public FonteResponse reativar(Long id) {
        FonteDoDado fonte = obter(id);

        fonte.setStatus("S");

        return resposta(fonte);
    }

    private void preencher(
        FonteDoDado fonte,
        FonteRequest request
    ) {
        if (request == null) {
            throw new RequisicaoInvalidaException(
                "Informe os dados da fonte."
            );
        }

        String nome = CadastroCampos.obrigatorio(
            request.nome(),
            "nome",
            120
        );

        String sigla = CadastroCampos.obrigatorio(
            request.sigla(),
            "sigla",
            20
        ).toUpperCase(Locale.ROOT);

        String orgao = CadastroCampos.obrigatorio(
            request.orgao(),
            "órgão responsável",
            160
        );

        String descricao = CadastroCampos.obrigatorio(
            request.descricao(),
            "descrição da origem",
            4000
        );

        String esfera = CadastroCampos.opcional(
            request.esfera(),
            "esfera",
            20
        );

        repository.findByNomeIgnoreCase(nome)
            .filter(existente ->
                !Objects.equals(existente.getId(), fonte.getId())
            )
            .ifPresent(existente -> {
                throw new ConflitoException(
                    "Já existe uma fonte com esse nome."
                );
            });

        repository.findBySiglaIgnoreCase(sigla)
            .filter(existente ->
                !Objects.equals(existente.getId(), fonte.getId())
            )
            .ifPresent(existente -> {
                throw new ConflitoException(
                    "Já existe uma fonte com essa sigla."
                );
            });

        fonte.setNome(nome);
        fonte.setSigla(sigla);
        fonte.setOrgao(orgao);
        fonte.setEsfera(esfera);
        fonte.setDescricao(descricao);
    }

    private FonteResponse resposta(FonteDoDado fonte) {
        return new FonteResponse(
            fonte.getId(),
            fonte.getNome(),
            fonte.getSigla(),
            fonte.getOrgao(),
            fonte.getEsfera(),
            fonte.getDescricao(),
            "S".equals(fonte.getStatus()),
            fonte.getDataCadastro()
        );
    }
}