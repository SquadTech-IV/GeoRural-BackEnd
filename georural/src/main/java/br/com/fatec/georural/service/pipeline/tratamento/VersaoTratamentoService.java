package br.com.fatec.georural.service.pipeline.tratamento;

import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.VersaoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

// decide entre reaproveitar uma versao existente e criar uma nova
@Slf4j
@Service
@RequiredArgsConstructor
public class VersaoTratamentoService {

    private final VersaoRepository versaoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public VersaoResolvida resolver(Long conjuntoId, Long execucaoId, Long usuarioId,
                                    String competencia, String impressao) {
        Optional<Versao> igual =
                versaoRepository.findFirstByConjunto_IdAndHashOrderByNumeroDesc(conjuntoId, impressao);

        if (igual.isPresent()) {
            Versao existente = igual.get();
            log.info("Conteudo identico a versao v{} (hash {}): nenhuma versao nova foi criada.",
                    existente.getNumero(), impressao.substring(0, 12));
            return new VersaoResolvida(existente.getId(), existente.getNumero(), false,
                    existente.getSituacao());
        }

        Long maior = versaoRepository.maiorNumero(conjuntoId);
        long numero = maior == null ? 1L : maior + 1L;

        Versao nova = new Versao();
        nova.setConjunto(entityManager.getReference(ConjuntoDados.class, conjuntoId));
        nova.setExecucao(entityManager.getReference(ExecucaoPipeline.class, execucaoId));
        if (usuarioId != null) {
            nova.setUsuario(entityManager.getReference(Usuario.class, usuarioId));
        }
        nova.setNumero(numero);
        nova.setHash(impressao);
        nova.setCompetencia(competencia);
        nova.setSituacao("RASCUNHO");
        nova.setDataCriacao(LocalDateTime.now());

        Versao salva = versaoRepository.save(nova);
        log.info("Versao v{} criada como RASCUNHO (hash {}).", numero, impressao.substring(0, 12));
        return new VersaoResolvida(salva.getId(), numero, true, "RASCUNHO");
    }

    public record VersaoResolvida(Long id, Long numero, boolean nova, String situacao) {
    }
}
