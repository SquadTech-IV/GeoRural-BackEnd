package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.service.PublicacaoService;
import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorVersaoService;
import br.com.fatec.georural.service.pipeline.tratamento.RegistroTransformacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// etapa 6 - PUBLICACAO: nao roda no automatico; usa o mesmo servico do endpoint
// POST /api/versoes/{id}/publicar, com as mesmas travas de qualidade
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicacaoEtapa implements EtapaPipeline {

    private final ResolvedorVersaoService resolvedorVersao;
    private final PublicacaoService publicacaoService;
    private final RegistroTransformacaoService transformacoes;
    private final JdbcTemplate jdbc;

    @Override
    public Etapa etapa() {
        return Etapa.PUBLICACAO;
    }

    @Override
    @Transactional
    public ResultadoEtapa executar(ContextoExecucao ctx) {
        Long execucaoId = exigir(ctx.getExecucaoIdAtual(), "execucaoIdAtual");
        Long versaoId = resolvedorVersao.versaoDaRodada(ctx);
        String justificativa = ctx.getParametros().get("justificativa");

        VersaoResponse versao = publicacaoService.publicar(versaoId, justificativa);
        long vigentes = contarVigentes(versaoId);

        RegistroTransformacaoService.Trilha trilha = transformacoes.abrir();
        trilha.passo("OUTRO", "Versao v" + versao.numero() + " publicada: "
                + vigentes + " resultado(s) de indicador passaram a vigentes.",
                "VERSAO", "ZONA PUBLICADA");
        transformacoes.gravar(execucaoId, trilha.passos());

        return ResultadoEtapa.de(vigentes, 0,
                "Versao v" + versao.numero() + " vigente com " + vigentes + " resultado(s).")
                .comVersao(versaoId);
    }

    private long contarVigentes(Long versaoId) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM RESULTADO_INDICADOR WHERE ver_id = ? AND rin_vigente = 'S'",
                Long.class, versaoId);
        return total == null ? 0 : total;
    }

    private Long exigir(Long valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException("A etapa PUBLICACAO precisa de " + nome + ".");
        }
        return valor;
    }
}
