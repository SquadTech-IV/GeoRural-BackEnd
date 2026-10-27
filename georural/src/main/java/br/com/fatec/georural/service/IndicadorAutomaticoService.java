package br.com.fatec.georural.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ReservaLegalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recalcula automaticamente os indicadores dos imóveis afetados logo após um processamento concluir.
 *
 * <p>Deve ser chamado DEPOIS do commit do processamento, dentro da mesma requisição HTTP: assim os dados
 * recém-gravados já estão visíveis e o lazy loading (open-in-view) funciona.
 *
 * <p>Cada cálculo fica isolado em try/catch: a falha de um imóvel não interrompe os demais nem a resposta
 * do processamento (que já foi concluído). Hoje recalcula o IRL; o IAE entra quando o fluxo de embargo for
 * validado (basta um novo case abaixo chamando o serviço do IAE).
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
                    // Imóvel recém-processado: recalcula IRL (0% enquanto não houver RL) e IAE (0 se nenhum
                    // embargo sobrepõe) dos imóveis deste arquivo.
                    List<Long> imovelIds = imovelRepository.findByArquivoBruto_Id(arquivoBrutoId)
                            .stream()
                            .map(ImovelRural::getId)
                            .toList();
                    recalcularIRL(imovelIds);
                    recalcularIAE(imovelIds);
                }
                case "RESERVA_LEGAL" -> {
                    // RL recém-processada: recalcula o IRL dos imóveis aos quais ela se ligou.
                    List<Long> imovelIds = reservaLegalRepository.findImovelIdsByArquivoBrutoId(arquivoBrutoId);
                    recalcularIRL(imovelIds);
                }
                case "EMBARGO" -> {
                    // Embargo é espacial (não aponta um imóvel): recalcula o IAE de todos os imóveis.
                    // Quem não intersecta nenhum embargo fica com IAE 0.
                    List<Long> imovelIds = imovelRepository.findAll()
                            .stream()
                            .map(ImovelRural::getId)
                            .toList();
                    recalcularIAE(imovelIds);
                }
                default -> {
                    // tipo sem indicador automático
                }
            }
        } catch (Exception e) {
            // O processamento já foi concluído; um erro no recálculo não deve derrubar a resposta.
            log.warn("Falha ao recalcular indicadores automaticamente para o arquivo id={}: {}",
                    arquivoBrutoId, e.getMessage(), e);
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
