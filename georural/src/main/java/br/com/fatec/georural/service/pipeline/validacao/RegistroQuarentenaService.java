package br.com.fatec.georural.service.pipeline.validacao;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Quarentena;
import br.com.fatec.georural.entity.RegraQualidade;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.repository.RegraQualidadeRepository;
import br.com.fatec.georural.repository.ValidacaoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// escreve a quarentena com etapa, feicao, valor esperado e valor encontrado
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroQuarentenaService {

    private final ValidacaoRepository validacaoRepository;
    private final RegraQualidadeRepository regraRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, Long> idsDeRegra = new ConcurrentHashMap<>();

    // abre a validacao do arquivo nesta execucao
    @Transactional
    public Long abrirValidacao(Long arquivoId, Long execucaoId) {
        Validacao validacao = new Validacao();
        validacao.setArquivoBruto(entityManager.getReference(ArquivoBruto.class, arquivoId));
        validacao.setExecucao(entityManager.getReference(ExecucaoPipeline.class, execucaoId));
        validacao.setDataExecucao(LocalDateTime.now());
        return validacaoRepository.save(validacao).getId();
    }

    @Transactional
    public void fecharValidacao(Long validacaoId, long total, long validos, long invalidos) {
        Validacao validacao = validacaoRepository.findById(validacaoId).orElseThrow();
        validacao.setTotalRegistros(total);
        validacao.setRegistrosValidos(validos);
        validacao.setRegistrosInvalidos(invalidos);
        validacao.setStatusFinal(validos > 0 ? "APROVADO" : "REJEITADO");
        validacaoRepository.save(validacao);
    }

    @Transactional
    public int registrarLote(Long validacaoId, String etapa, List<RegistroPendente> pendentes) {
        if (pendentes.isEmpty()) {
            return 0;
        }
        Validacao validacao = entityManager.getReference(Validacao.class, validacaoId);
        LocalDateTime agora = LocalDateTime.now();

        for (RegistroPendente pendente : pendentes) {
            ProblemaFeicao problema = pendente.problema();
            Quarentena linha = new Quarentena();
            linha.setValidacao(validacao);
            linha.setRegra(regraPorNome(problema.regra()));
            linha.setEtapa(etapa);
            linha.setIdentificador(cortar(pendente.identificador(), 120));
            linha.setLinhaOrigem(pendente.linha());
            linha.setColunaErro(cortar(problema.campo(), 60));
            linha.setValorEncontrado(cortar(problema.valorEncontrado(), 400));
            linha.setValorEsperado(cortar(problema.valorEsperado(), 400));
            linha.setMotivoErro(cortar(problema.motivo(), 400));
            linha.setDadoOriginal(pendente.dadoOriginalJson());
            linha.setSituacao("PENDENTE");
            linha.setDataRegistro(agora);
            entityManager.persist(linha);
        }

        entityManager.flush();
        entityManager.clear();
        return pendentes.size();
    }

    // regra de qualidade pelo nome do V2; nulo se o nome nao estiver cadastrado
    private RegraQualidade regraPorNome(String nome) {
        if (nome == null) {
            return null;
        }
        Long id = idsDeRegra.computeIfAbsent(nome, chave ->
                regraRepository.findByNome(chave).map(RegraQualidade::getId).orElse(-1L));
        if (id == null || id < 0) {
            log.warn("Regra de qualidade '{}' nao esta cadastrada em REGRA_QUALIDADE.", nome);
            return null;
        }
        return entityManager.getReference(RegraQualidade.class, id);
    }

    private String cortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max - 3) + "...";
    }

    // uma linha de quarentena antes de ser gravada
    public record RegistroPendente(
            String identificador,
            Long linha,
            String dadoOriginalJson,
            ProblemaFeicao problema) {

        public static RegistroPendente de(FeicaoPadronizada feicao, ProblemaFeicao problema) {
            return new RegistroPendente(
                    feicao.identificador(),
                    feicao.linha(),
                    JsonSimples.deMapa(feicao.atributosOriginais()),
                    problema);
        }
    }
}
