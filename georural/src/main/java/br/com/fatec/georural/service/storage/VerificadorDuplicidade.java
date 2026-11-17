package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificadorDuplicidade {

    private final ArquivoBrutoRepository repositorio;

    @Value("${georural.recebimento.prazo-abandono-minutos:15}")
    private long prazoAbandonoMinutos;


    public Optional<ArquivoBruto> verificar(Long conjuntoId, String competencia, String hash) {
        if (conjuntoId == null) {
            throw new IllegalArgumentException("Identificador do conjunto é obrigatório.");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Hash é obrigatório.");
        }
        if (competencia == null || competencia.isBlank()) {
            throw new IllegalArgumentException("Competência é obrigatória para identificar o arquivo.");
        }
        String hashNormalizado = hash.trim().toLowerCase();

        Optional<ArquivoBruto> existente =
                repositorio.buscarPorIdentidade(conjuntoId, competencia, hashNormalizado);
        if (existente.isEmpty()) {
            avisarSeConteudoRepetidoEmOutraCompetencia(conjuntoId, competencia, hashNormalizado);
            return Optional.empty();
        }
        if (estaAbandonado(existente.get())) {
            return existente;
        }
        throw new ArquivoDuplicadoException(conjuntoId, hashNormalizado, existente.get().getId());
    }


    private void avisarSeConteudoRepetidoEmOutraCompetencia(Long conjuntoId, String competencia, String hash) {
        var outras = repositorio.buscarMesmoConteudoNoConjunto(conjuntoId, hash);
        if (!outras.isEmpty()) {
            log.info("Conteudo identico ja existe no conjunto {} nas competencias {}. "
                            + "Entrando assim mesmo como competencia {} -- conteudo repetido entre periodos "
                            + "costuma indicar que a fonte nao atualizou os dados.",
                    conjuntoId,
                    outras.stream().map(ArquivoBruto::getCompetencia).distinct().toList(),
                    competencia);
        }
    }

    private boolean estaAbandonado(ArquivoBruto arquivo) {
        return SituacaoArquivoBruto.RECEBENDO.equals(arquivo.getStatus())
                && arquivo.getDataUpload() != null
                && arquivo.getDataUpload().isBefore(LocalDateTime.now().minusMinutes(prazoAbandonoMinutos));
    }
}
