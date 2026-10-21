package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.SituacaoVersao;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.AuditoriaRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.qualidade.QualidadeService;
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
    private final AuditoriaRepository auditoriaRepository;
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

        List<Versao> vigentes = versaoRepository.findByConjunto_IdAndSituacao(
                conjunto.getId(), SituacaoVersao.VIGENTE.name());
        for (Versao vigente : vigentes) {
            vigente.setSituacao(SituacaoVersao.ANTERIOR.name());
        }
        versaoRepository.saveAll(vigentes);

        // publica esta versao
        versao.setSituacao(SituacaoVersao.VIGENTE.name());
        versao.setDataPublicacao(LocalDateTime.now());
        versaoRepository.save(versao);

        // aponta a vigente do conjunto para esta versao
        conjunto.setVersaoVigente(versao);
        conjuntoDadosRepository.save(conjunto);

        return toResponse(versao);
    }

    @Transactional
    public VersaoResponse reprovar(Long versaoId, String motivo) {
        Versao versao = versaoRepository.findById(versaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versao nao encontrada: " + versaoId));

        if (motivo == null || motivo.isBlank()) {
            throw new ConflitoException("Informe o motivo da reprovacao da versao " + versaoId + ".");
        }

        SituacaoVersao situacao = SituacaoVersao.de(versao.getSituacao());
        if (situacao != SituacaoVersao.RASCUNHO) {
            throw new ConflitoException("Versao " + versaoId
                    + " nao pode ser reprovada: situacao atual e " + situacao
                    + " (apenas RASCUNHO pode ser reprovada).");
        }

        Auditoria registro = new Auditoria();
        registro.setOperacao("UPDATE");
        registro.setEntidadeAfetada("VERSAO");
        registro.setRegistroId(versaoId);
        registro.setDadosNovos("{\"acao\":\"REPROVACAO\",\"motivo\":\"" + escaparJson(motivo.trim()) + "\"}");
        registro.setDataHora(LocalDateTime.now());
        auditoriaRepository.save(registro);

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

    private static String escaparJson(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}