package br.com.fatec.georural.service.pipeline.calculo;

import br.com.fatec.georural.service.indicador.PeriodoApuracaoIfc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// decide ate que dia os focos sao contados. Ordem: data informada na rodada > competencia
// da rodada > competencia da versao > hoje
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolvedorPeriodoIfc {

    private final JdbcTemplate jdbc;

    public Resolvido resolver(Long versaoId, String competenciaDaRodada,
                              String dataReferenciaInformada, int meses) {
        if (dataReferenciaInformada != null && !dataReferenciaInformada.isBlank()) {
            try {
                LocalDate fim = LocalDate.parse(dataReferenciaInformada.trim());
                return new Resolvido(PeriodoApuracaoIfc.terminandoEm(fim, meses),
                        "parametro dataReferenciaIfc (" + fim + ")");
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("dataReferenciaIfc '" + dataReferenciaInformada
                        + "' nao e uma data ISO (aaaa-mm-dd).", e);
            }
        }

        if (competenciaDaRodada != null && !competenciaDaRodada.isBlank()) {
            return new Resolvido(PeriodoApuracaoIfc.daCompetencia(competenciaDaRodada, meses),
                    "competencia da rodada (" + competenciaDaRodada.trim() + ")");
        }

        String daVersao = competenciaDaVersao(versaoId);
        if (daVersao != null && !daVersao.isBlank()) {
            return new Resolvido(PeriodoApuracaoIfc.daCompetencia(daVersao, meses),
                    "competencia da versao (" + daVersao.trim() + ")");
        }

        log.warn("Nenhuma competencia para a versao {}: o periodo do IFC termina hoje.", versaoId);
        return new Resolvido(PeriodoApuracaoIfc.terminandoEm(LocalDate.now(), meses),
                "data de hoje (nenhuma competencia informada)");
    }

    private String competenciaDaVersao(Long versaoId) {
        if (versaoId == null) {
            return null;
        }
        try {
            return jdbc.queryForObject(
                    "SELECT ver_competencia FROM VERSAO WHERE ver_id = ?", String.class, versaoId);
        } catch (DataAccessException e) {
            log.debug("Sem competencia para a versao {}: {}", versaoId, e.getMessage());
            return null;
        }
    }

    public record Resolvido(PeriodoApuracaoIfc periodo, String origem) {
    }
}
