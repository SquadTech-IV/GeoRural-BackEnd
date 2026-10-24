package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DuplicidadeService {

    private final ArquivoBrutoRepository arquivoBrutoRepository;

    /** Ja existe arquivo com esse nome nesse conjunto?  */
    @Transactional(readOnly = true)
    public boolean jaRecebido(Long conjuntoId, String nomeArquivo) {
        return conjuntoId != null && nomeArquivo != null
                && arquivoBrutoRepository.existsByConjunto_IdAndNome(conjuntoId, nomeArquivo);
    }

    /** Ja existe conteudo com esse hash no datalake? */
    @Transactional(readOnly = true)
    public boolean jaExistePorHash(String hash) {
        return hash != null && arquivoBrutoRepository.findByHash(hash).isPresent();
    }

    /** Trava combinada da importacao : ja recebido por nome OU conteudo identico por hash. */
    @Transactional(readOnly = true)
    public boolean duplicado(Long conjuntoId, String nomeArquivo, String hash) {
        return jaRecebido(conjuntoId, nomeArquivo) || jaExistePorHash(hash);
    }
}