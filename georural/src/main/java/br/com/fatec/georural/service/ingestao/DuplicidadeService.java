package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicidadeService {

    private final ArquivoBrutoRepository arquivoBrutoRepository;

    /** Ja existe esse nome nesse conjunto? */
    @Transactional(readOnly = true)
    public boolean jaRecebido(Long conjuntoId, String nomeArquivo) {
        return conjuntoId != null && nomeArquivo != null
                && arquivoBrutoRepository.existsByConjunto_IdAndNome(conjuntoId, nomeArquivo);
    }

    /** Duplicata de verdade: mesmo conjunto + mesma competencia + mesmo conteudo. */
    @Transactional(readOnly = true)
    public boolean jaExiste(Long conjuntoId, String competencia, String hash) {
        if (conjuntoId == null || hash == null || competencia == null) {
            return false;
        }
        return arquivoBrutoRepository
                .buscarPorIdentidade(conjuntoId, competencia, normalizar(hash))
                .isPresent();
    }

    /**
     * O mesmo conteudo ja entrou em OUTRA competencia deste conjunto
     */
    @Transactional(readOnly = true)
    public List<String> competenciasComMesmoConteudo(Long conjuntoId, String competencia, String hash) {
        if (conjuntoId == null || hash == null) {
            return List.of();
        }
        return arquivoBrutoRepository.buscarMesmoConteudoNoConjunto(conjuntoId, normalizar(hash))
                .stream()
                .map(ArquivoBruto::getCompetencia)
                .filter(c -> c != null && !c.equals(competencia))
                .distinct()
                .toList();
    }

    /** Trava combinada usada na ingestao: nome repetido OU conteudo repetido na mesma competencia. */
    @Transactional(readOnly = true)
    public boolean duplicado(Long conjuntoId, String nomeArquivo, String competencia, String hash) {
        return jaRecebido(conjuntoId, nomeArquivo) || jaExiste(conjuntoId, competencia, hash);
    }

    private String normalizar(String hash) {
        return hash.trim().toLowerCase();
    }
}
