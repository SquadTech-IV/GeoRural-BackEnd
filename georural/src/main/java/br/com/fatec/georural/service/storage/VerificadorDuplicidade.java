package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Bloqueia o recebimento de arquivo com hash idêntico a um já registrado para o mesmo conjunto.
 *
 * <p>Esta verificação rejeita o caso comum cedo e com mensagem clara. A garantia contra envios
 * simultâneos é o índice único {@code uk_arq_con_hash} do banco: quem grava o registro deve tratar a
 * violação desse índice como duplicado.
 *
 * <p>Um registro RECEBENDO mais antigo que o prazo de abandono é considerado abandonado (o processo
 * pode ter morrido depois de reservar a vaga). Nesse caso ele é devolvido para ser retomado, em vez de
 * bloquear o reenvio para sempre.
 */
@Component
@RequiredArgsConstructor
public class VerificadorDuplicidade {

    private final ArquivoBrutoRepository repositorio;

    // Consta o tempo em que o arquivo está em RECEBENDO antes de ser considerado abandonado. O valor padrão é 15 minutos.
    @Value("${georural.recebimento.prazo-abandono-minutos:15}")
    private long prazoAbandonoMinutos;

    /**
     * @return vazio se não há arquivo ativo com esse hash no conjunto; ou o registro RECEBENDO
     *         abandonado, que deve ser retomado
     * @throws ArquivoDuplicadoException se há arquivo ativo que não está abandonado
     * @throws IllegalArgumentException  se o conjunto ou o hash não forem informados
     */
    public Optional<ArquivoBruto> verificar(Long conjuntoId, String hash) {
        if (conjuntoId == null) {
            throw new IllegalArgumentException("Identificador do conjunto é obrigatório.");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Hash é obrigatório.");
        }
        String hashNormalizado = hash.trim().toLowerCase();

        Optional<ArquivoBruto> ativo = repositorio.findArquivoBruto(conjuntoId, hashNormalizado);
        if (ativo.isEmpty()) {
            return Optional.empty();
        }
        if (estaAbandonado(ativo.get())) {
            return ativo;
        }
        throw new ArquivoDuplicadoException(conjuntoId, hashNormalizado);
    }

    private boolean estaAbandonado(ArquivoBruto arquivo) {
        return SituacaoArquivoBruto.RECEBENDO.equals(arquivo.getStatus())
                && arquivo.getDataUpload() != null
                && arquivo.getDataUpload().isBefore(LocalDateTime.now().minusMinutes(prazoAbandonoMinutos));
    }
}