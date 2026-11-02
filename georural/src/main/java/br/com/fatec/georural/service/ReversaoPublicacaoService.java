package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.entity.SituacaoVersao;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.AuditoriaRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReversaoPublicacaoService {

    private final VersaoRepository versaoRepository;
    private final ConjuntoDadosRepository conjuntoDadosRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final MovimentacaoZonaRepository movimentacaoZonaRepository;
    private final CalculoOracle calculoOracle;

    // devolve a vigencia para a versao anterior do mesmo conjunto
    @Transactional
    public VersaoResponse reverter(Long versaoId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ConflitoException("Informe o motivo da reversao da versao " + versaoId + ".");
        }

        Versao vigente = versaoRepository.findById(versaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versao nao encontrada: " + versaoId));

        SituacaoVersao situacao = SituacaoVersao.de(vigente.getSituacao());
        if (situacao != SituacaoVersao.VIGENTE) {
            throw new ConflitoException("Versao " + versaoId
                    + " nao pode ser revertida: situacao atual e " + situacao
                    + " (apenas VIGENTE pode ser revertida).");
        }

        ConjuntoDados conjunto = vigente.getConjunto();

        // a anterior mais recente volta a valer
        Versao anterior = versaoRepository
                .findByConjunto_IdAndSituacao(conjunto.getId(), SituacaoVersao.ANTERIOR.name())
                .stream()
                .max(Comparator.comparing(Versao::getNumero))
                .orElseThrow(() -> new ConflitoException(
                        "A versao " + versaoId + " nao tem versao anterior para onde voltar. "
                                + "Ela e a primeira publicada deste conjunto."));

        vigente.setSituacao(SituacaoVersao.ANTERIOR.name());
        versaoRepository.save(vigente);

        anterior.setSituacao(SituacaoVersao.VIGENTE.name());
        versaoRepository.save(anterior);

        conjunto.setVersaoVigente(anterior);
        conjuntoDadosRepository.save(conjunto);

        // religa os resultados da anterior e apaga os da que saiu
        calculoOracle.publicar(anterior.getId());

        registrarMovimentacao(vigente, anterior);
        registrarAuditoria(vigente, anterior, motivo.trim());

        return toResponse(anterior);
    }

    private void registrarMovimentacao(Versao saiu, Versao entrou) {
        if (saiu.getExecucao() == null || saiu.getExecucao().getArquivoBruto() == null) {
            return;
        }

        MovimentacaoZona movimentacao = new MovimentacaoZona();
        movimentacao.setArquivoBruto(saiu.getExecucao().getArquivoBruto());
        movimentacao.setExecucao(saiu.getExecucao());
        movimentacao.setZona("PUBLICADA");
        movimentacao.setStatus("CONCLUIDO");
        movimentacao.setObservacao("Versao v" + saiu.getNumero() + " revertida; v"
                + entrou.getNumero() + " voltou a ser a vigente");
        movimentacao.setData(LocalDateTime.now());
        movimentacaoZonaRepository.save(movimentacao);
    }

    private void registrarAuditoria(Versao saiu, Versao entrou, String motivo) {
        Auditoria registro = new Auditoria();
        registro.setOperacao("UPDATE");
        registro.setEntidadeAfetada("VERSAO");
        registro.setRegistroId(saiu.getId());
        registro.setDadosAntigos("{\"situacao\":\"VIGENTE\",\"versao\":" + saiu.getNumero() + "}");
        registro.setDadosNovos("{\"acao\":\"REVERSAO\""
                + ",\"versao\":" + saiu.getNumero()
                + ",\"voltouPara\":" + entrou.getNumero()
                + ",\"motivo\":\"" + escaparJson(motivo) + "\"}");
        registro.setDataHora(LocalDateTime.now());
        auditoriaRepository.save(registro);
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

    // escapa o texto para ser embutido com seguranca em um valor JSON
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
