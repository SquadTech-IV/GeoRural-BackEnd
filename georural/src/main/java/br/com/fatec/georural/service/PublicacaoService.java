package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.SituacaoVersao;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicacaoService {

    private final VersaoRepository versaoRepository;
    private final ConjuntoDadosRepository conjuntoDadosRepository;
    private final QualidadeService qualidadeService;

    @Transactional
    public VersaoResponse publicar(Long versaoId) {
        Versao versao = versaoRepository.findById(versaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versao nao encontrada: " + versaoId));

        SituacaoVersao situacao = SituacaoVersao.de(versao.getSituacao());
        if (situacao != SituacaoVersao.RASCUNHO) {
            throw new ConflitoException("Versao " + versaoId
                    + " nao pode ser publicada: situacao atual e " + situacao
                    + " (apenas RASCUNHO pode ser publicada).");
        }

        ExecucaoPipeline execucao = versao.getExecucao();
        if (execucao == null) {
            throw new ConflitoException("Versao " + versaoId
                    + " nao possui execucao vinculada para conferir a qualidade.");
        }

        QualidadeResponse qualidade = qualidadeService.avaliar(execucao.getId());
        if (!qualidade.aprovado()) {
            throw new ConflitoException("Versao " + versaoId
                    + " nao pode ser publicada: qualidade reprovada. "
                    + String.join(" ", qualidade.motivosReprovacao()));
        }

        ConjuntoDados conjunto = versao.getConjunto();

        // rebaixa a(s) vigente(s) anterior(es) do mesmo conjunto
        List<Versao> vigentes = versaoRepository.findByConjunto_IdAndSituacao(
                conjunto.getId(), SituacaoVersao.VIGENTE.name());
        for (Versao vigente : vigentes) {
            vigente.setSituacao(SituacaoVersao.ANTERIOR.name());
        }
        versaoRepository.saveAll(vigentes);

        // publica esta versao: RASCUNHO -> VIGENTE
        versao.setSituacao(SituacaoVersao.VIGENTE.name());
        versao.setDataPublicacao(LocalDateTime.now());
        versaoRepository.save(versao);

        // aponta a vigente do conjunto para esta versao
        conjunto.setVersaoVigente(versao);
        conjuntoDadosRepository.save(conjunto);

        return toResponse(versao);
    }

    private VersaoResponse toResponse(Versao v) {
        return new VersaoResponse(
                v.getId(),
                v.getConjunto() != null ? v.getConjunto().getId() : null,
                v.getNumero(),
                v.getSituacao(),
                v.getCompetencia(),
                v.getDataPublicacao(),
                v.getExecucao() != null ? v.getExecucao().getId() : null);
    }
}