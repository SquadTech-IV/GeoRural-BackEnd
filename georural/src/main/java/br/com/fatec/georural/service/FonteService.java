package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.cadastro.FonteRequest;
import br.com.fatec.georural.dto.response.cadastro.FonteResponse;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.service.auditoria.AuditoriaService;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
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
    private final CadastroValidacaoService validacao;
    private final ConjuntoDadosRepository conjuntos;
    private final AuditoriaService auditoria;

    public FonteService(
            FonteDoDadoRepository repository,
            CadastroValidacaoService validacao,
            ConjuntoDadosRepository conjuntos,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.validacao = validacao;
        this.conjuntos = conjuntos;
        this.auditoria = auditoria;
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

        FonteDoDado salva = repository.saveAndFlush(fonte);

        // Cadastro de conjunto e de fonte nao entrava na trilha: nem aqui nem por
        // gatilho -- os gatilhos de auditoria cobrem so VERSAO, REGRA_CALCULO e
        // PARAMETRO_REGRA_CALCULO. Criar um conjunto nao aparecia no historico.
        auditoria.registrar("INSERT", "FONTE_DO_DADO", salva.getId(), null, retrato(salva));

        return resposta(salva);
    }

    @Transactional
    public FonteResponse atualizar(
            Long id,
            FonteRequest request) {
        FonteDoDado fonte = obter(id);

        String antes = retrato(fonte);
        preencher(fonte, request);

        FonteDoDado salva = repository.saveAndFlush(fonte);

        auditoria.registrar("UPDATE", "FONTE_DO_DADO", salva.getId(), antes, retrato(salva));

        return resposta(salva);
    }

    @Transactional
    public void desativar(Long id) {
        FonteDoDado fonte = obter(id);
        String antes = retrato(fonte);
        fonte.setStatus("N");

        auditoria.registrar("DESATIVACAO", "FONTE_DO_DADO", fonte.getId(), antes, retrato(fonte));
    }

    @Transactional
    public FonteResponse reativar(Long id) {
        FonteDoDado fonte = obter(id);

        String antes = retrato(fonte);
        fonte.setStatus("S");

        auditoria.registrar("REATIVACAO", "FONTE_DO_DADO", fonte.getId(), antes, retrato(fonte));

        return resposta(fonte);
    }

    // Retrato curto da fonte para a trilha. O escapar() trata nulo.
    private String retrato(FonteDoDado f) {
        return "{\"nome\":\"" + AuditoriaService.escapar(f.getNome()) + "\""
                + ",\"ativa\":" + "S".equals(f.getStatus()) + "}";
    }

    private void preencher(
            FonteDoDado fonte,
            FonteRequest request) {
        validacao.validarFonte(request);

        String nome = request.nome().trim();

        String sigla = request.sigla()
                .trim()
                .toUpperCase(Locale.ROOT);

        repository.findByNomeIgnoreCase(nome)
                .filter(existente ->
                        !Objects.equals(
                                existente.getId(),
                                fonte.getId()
                        )
                )
                .ifPresent(existente -> {
                    throw new ConflitoException(
                            "Já existe uma fonte com esse nome."
                    );
                });

        repository.findBySiglaIgnoreCase(sigla)
                .filter(existente ->
                        !Objects.equals(
                                existente.getId(),
                                fonte.getId()
                        )
                )
                .ifPresent(existente -> {
                    throw new ConflitoException(
                            "Já existe uma fonte com essa sigla."
                    );
                });
        fonte.setNome(nome);
        fonte.setSigla(sigla);

        fonte.setEsfera(
                CadastroCampos.opcional(
                        request.esfera(),
                        "esfera",
                        20
                )
        );
    }

    private List<String> tiposCadastrados(
            FonteDoDado fonte) {
        if (fonte.getId() == null) {
            return List.of();
        }

        return conjuntos.findByFonteIdOrderByNomeAsc(
                        fonte.getId()
                )
                .stream()
                .map(conjunto ->
                        conjunto.getTipoConjunto().getNome()
                )
                .distinct()
                .sorted()
                .toList();
    }

    private FonteResponse resposta(FonteDoDado fonte) {
        return new FonteResponse(
                fonte.getId(),
                fonte.getNome(),
                fonte.getSigla(),
                null,
                fonte.getEsfera(),
                null,
                "S".equals(fonte.getStatus()),
                fonte.getDataCadastro(),
                tiposCadastrados(fonte)
        );
    }
}