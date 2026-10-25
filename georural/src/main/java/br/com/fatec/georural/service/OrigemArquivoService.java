package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.cadastro.ConjuntoRequest;
import br.com.fatec.georural.dto.response.cadastro.OrigemArquivoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ArquivoDatalakeRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrigemArquivoService {

    private final ConjuntoDadosRepository conjuntoRepository;
    private final ArquivoDatalakeRepository arquivoRepository;
    private final CadastroValidacaoService validacao;

    public OrigemArquivoService(
        ConjuntoDadosRepository conjuntoRepository,
        ArquivoDatalakeRepository arquivoRepository,
        CadastroValidacaoService validacao
    ) {
        this.conjuntoRepository = conjuntoRepository;
        this.arquivoRepository = arquivoRepository;
        this.validacao = validacao;
    }

    public ConjuntoDados obterConjuntoParaUpload(Long conjuntoId) {
        CadastroCampos.validarId(
            conjuntoId,
            "o conjunto do arquivo"
        );

        ConjuntoDados conjunto = conjuntoRepository
            .findById(conjuntoId)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException(
                    "Conjunto não encontrado: " + conjuntoId
                )
            );

        FonteDoDado fonte = conjunto.getFonte();

        if (fonte == null) {
            throw new ConflitoException(
                "O conjunto não possui uma fonte associada."
            );
        }

        if (!"S".equals(fonte.getStatus())) {
            throw new ConflitoException(
                "Não é possível enviar arquivos para uma fonte desativada."
            );
        }

        if ("ARQUIVADO".equals(conjunto.getSituacao())) {
            throw new ConflitoException(
                "Não é possível enviar arquivos para um conjunto arquivado."
            );
        }

        if (!conjunto.permiteUpload()) {
            throw new ConflitoException("Este conjunto não permite envio manual de arquivos.");
        }

        validacao.validarConjunto(
            new ConjuntoRequest(
                fonte.getId(),
                conjunto.getNome(),
                conjunto.getTipoCamada(),
                conjunto.getDescricao(),
                conjunto.getEsquemaTecnico(),
                conjunto.getSrs(),
                conjunto.getCompetencia(),
                conjunto.getCobertura(),
                conjunto.getPeriodicidade()
            )
        );

        return conjunto;
    }

    public OrigemArquivoResponse consultar(Long arquivoId) {
        CadastroCampos.validarId(
            arquivoId,
            "o arquivo"
        );

        ArquivoDatalake arquivo = arquivoRepository
            .findById(arquivoId)
            .orElseThrow(() ->
                new RecursoNaoEncontradoException(
                    "Arquivo não encontrado: " + arquivoId
                )
            );

        ConjuntoDados conjunto = arquivo.getConjunto();

        if (conjunto == null) {
            throw new ConflitoException(
                "Este arquivo antigo ainda não possui conjunto associado."
            );
        }

        return new OrigemArquivoResponse(
            arquivo.getId(),
            arquivo.getNomeArquivo(),
            conjunto.getFonte().getId(),
            arquivo.getFonte(),
            conjunto.getId(),
            conjunto.getNome(),
            arquivo.getTipoCamada(),
            arquivo.getSrid(),
            arquivo.getCompetenciaOrigem(),
            arquivo.getCoberturaOrigem(),
            arquivo.getRecebidoEm()
        );
    }
}