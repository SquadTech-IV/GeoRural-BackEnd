package br.com.fatec.georural.service.storage;

import org.springframework.stereotype.Component;

import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import lombok.RequiredArgsConstructor;

/**
 * Valida a existência e a elegibilidade do conjunto de dados para recebimento de arquivos.
 *
 * <p>Regras:
 * <ul>
 *   <li>O conjunto deve existir (caso contrário, lança {@link RecursoNaoEncontradoException}).</li>
 *   <li>O conjunto não pode estar com situação 'ARQUIVADO' (lança {@link RequisicaoInvalidaException}).</li>
 *   <li>A forma de entrada deve ser 'UPLOAD' ou 'AMBOS' (lança {@link RequisicaoInvalidaException}).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ValidadorConjuntoDados {

    private static final String SITUACAO_ARQUIVADO = "ARQUIVADO";
    private static final String FORMA_UPLOAD = "UPLOAD";
    private static final String FORMA_AMBOS = "AMBOS";

    private final ConjuntoDadosRepository conjuntoRepository;

    public ConjuntoDados validar(Long conjuntoId) {
        if (conjuntoId == null) {
            throw new RequisicaoInvalidaException("Identificador do conjunto é obrigatório.");
        }
        ConjuntoDados conjunto = conjuntoRepository.findById(conjuntoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conjunto de dados não encontrado: " + conjuntoId));

        validar(conjunto);
        return conjunto;
    }

    public void validar(ConjuntoDados conjunto) {
        if (conjunto == null) {
            throw new RecursoNaoEncontradoException("Conjunto de dados não informado.");
        }
        if (SITUACAO_ARQUIVADO.equalsIgnoreCase(conjunto.getSituacao())) {
            throw new RequisicaoInvalidaException("Conjunto de dados está arquivado e não pode receber arquivos.");
        }
        String forma = conjunto.getFormaEntrada();
        if (forma == null || (!FORMA_UPLOAD.equalsIgnoreCase(forma) && !FORMA_AMBOS.equalsIgnoreCase(forma))) {
            throw new RequisicaoInvalidaException(
                    "Forma de entrada do conjunto de dados não permite envio por upload. Forma atual: " + forma);
        }
    }
}