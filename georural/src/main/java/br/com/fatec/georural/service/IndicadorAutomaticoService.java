package br.com.fatec.georural.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import br.com.fatec.georural.repository.ReservaLegalRepository;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recalcula automaticamente os indicadores dos imóveis afetados logo após um processamento concluir.
 *
 * <p>Deve ser chamado DEPOIS do commit do processamento, dentro da mesma requisição HTTP: assim os dados
 * recém-gravados já estão visíveis e o lazy loading (open-in-view) funciona.
 *
 * <p>Cada cálculo fica isolado em try/catch: a falha de um imóvel não interrompe os demais nem a resposta
 * do processamento (que já foi concluído).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicadorAutomaticoService {

    private final ArquivoBrutoRepository arquivoRepository;
    private final ImovelRuralRepository imovelRepository;
    private final ReservaLegalRepository reservaLegalRepository;
    private final CalculoIRLGeoService calculoIRLService;
    private final CalculoIAEService calculoIAEService;
    private final IndicadorRepository indicadorRepository;
    private final RegraCalculoRepository regraCalculoRepository;
    private final CalculoOracle calculoOracle;

    /**
     * Dispara o recálculo dos indicadores conforme o tipo do que acabou de ser processado.
     *
     * @param arquivoBrutoId arquivo bruto recém-processado
     */
    public void recalcularAposProcessamento(Long arquivoBrutoId) {
        try {
            ArquivoBruto arquivo = arquivoRepository.findById(arquivoBrutoId).orElse(null);
            if (arquivo == null || arquivo.getConjunto() == null) {
                return;
            }

            String tipo = arquivo.getConjunto().getTipoCamada();
            if (tipo == null) {
                return;
            }

            switch (tipo.trim().toUpperCase()) {
                case "IMOVEL" -> {
                    List<ImovelRural> imoveis = imovelRepository.findByArquivoBruto_Id(arquivoBrutoId);
                    List<Long> imovelIds = imoveis.stream()
                            .map(ImovelRural::getId)
                            .toList();
                    recalcularIRL(imovelIds);
                    recalcularIAE(imovelIds);

                    imoveis.stream()
                            .filter(i -> i.getVersao() != null)
                            .map(i -> i.getVersao().getId())
                            .distinct()
                            .forEach(versaoId -> recalcularISAPVersao(versaoId, "IMOVEL", null));
                }
                case "RESERVA_LEGAL" -> {
                    List<Long> imovelIds = reservaLegalRepository.findImovelIdsByArquivoBrutoId(arquivoBrutoId);
                    recalcularIRL(imovelIds);
                }
                case "EMBARGO" -> {
                    List<Long> imovelIds = imovelRepository.findAll()
                            .stream()
                            .map(ImovelRural::getId)
                            .toList();
                    recalcularIAE(imovelIds);
                }
                case "AREA_PROTEGIDA" -> {
                    imovelRepository.findAll().stream()
                            .filter(i -> i.getVersao() != null)
                            .map(i -> i.getVersao().getId())
                            .distinct()
                            .forEach(versaoId -> recalcularISAPVersao(versaoId, "TODOS", null));
                }
                default -> {
                    // tipo sem indicador automático
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao recalcular indicadores automaticamente para o arquivo id={}: {}",
                    arquivoBrutoId, e.getMessage(), e);
        }
    }

    private void recalcularISAPVersao(Long versaoId, String escopo, Long camadaVersaoId) {
        try {
            Indicador indicador = indicadorRepository.findBySigla("ISAP").orElse(null);
            if (indicador == null) {
                log.warn("Indicador ISAP não cadastrado; recálculo automático ignorado.");
                return;
            }

            RegraCalculo regra = regraCalculoRepository.findByIndicadorAndVigente(indicador, "S").orElse(null);
            if (regra == null) {
                log.warn("Nenhuma regra vigente encontrada para ISAP; recálculo automático ignorado.");
                return;
            }

            long calculados = calculoOracle.calcularIsap(versaoId, null, regra.getId(), escopo, camadaVersaoId);
            if (calculados > 0) {
                calculoOracle.publicar(versaoId);
            }
            log.info("Recálculo automático do ISAP concluído na versão id={}: {} imóvel(eis).",
                    versaoId, calculados);
        } catch (Exception e) {
            log.warn("Falha ao recalcular ISAP na versão id={}: {}", versaoId, e.getMessage(), e);
        }
    }

    private void recalcularIRL(List<Long> imovelIds) {
        if (imovelIds == null || imovelIds.isEmpty()) {
            log.info("Recálculo automático do IRL: nenhum imóvel afetado.");
            return;
        }

        int ok = 0;
        for (Long imovelId : imovelIds) {
            try {
                calculoIRLService.calcularReservaLegal(imovelId);
                ok++;
            } catch (Exception e) {
                log.warn("Falha ao recalcular IRL do imóvel id={}: {}", imovelId, e.getMessage(), e);
            }
        }
        log.info("Recálculo automático do IRL concluído: {}/{} imóveis.", ok, imovelIds.size());
    }

    private void recalcularIAE(List<Long> imovelIds) {
        if (imovelIds == null || imovelIds.isEmpty()) {
            log.info("Recálculo automático do IAE: nenhum imóvel afetado.");
            return;
        }

        int ok = 0;
        for (Long imovelId : imovelIds) {
            try {
                calculoIAEService.calcularSobreposicaoEmbargo(imovelId);
                ok++;
            } catch (Exception e) {
                log.warn("Falha ao recalcular IAE do imóvel id={}: {}", imovelId, e.getMessage(), e);
            }
        }
        log.info("Recálculo automático do IAE concluído: {}/{} imóveis.", ok, imovelIds.size());
    }
}