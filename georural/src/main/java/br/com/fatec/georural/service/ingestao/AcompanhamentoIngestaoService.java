package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.IngestaoRecenteResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AcompanhamentoIngestaoService {

    private final ConjuntoDadosRepository conjuntoRepository;
    private final ArquivoBrutoRepository arquivoBrutoRepository;
    private final MovimentacaoZonaRepository movimentacaoZonaRepository;

    private static final Pattern QTD = Pattern.compile("(\\d[\\d.]*)\\s*registro", Pattern.CASE_INSENSITIVE);

    @Transactional(readOnly = true)
    public List<IngestaoRecenteResponse> recentes(Long conjuntoId, int limite) {
        if (!conjuntoRepository.existsById(conjuntoId)) {
            throw new RecursoNaoEncontradoException("Conjunto nao encontrado: " + conjuntoId);
        }
        int tamanho = (limite <= 0 || limite > 50) ? 10 : limite;

        List<ArquivoBruto> arquivos = arquivoBrutoRepository
                .findByConjunto_IdOrderByDataUploadDesc(conjuntoId, PageRequest.of(0, tamanho));

        List<IngestaoRecenteResponse> out = new ArrayList<>();
        for (ArquivoBruto a : arquivos) {
            List<MovimentacaoZona> movs = movimentacaoZonaRepository.findByArquivoBrutoIdOrderByDataAsc(a.getId());
            String zonaAtual = movs.isEmpty() ? null : movs.get(movs.size() - 1).getZona();
            out.add(new IngestaoRecenteResponse(
                    a.getId(), a.getNome(), a.getFormato(), a.getTamanhoBytes(),
                    a.getDataUpload(), a.getStatus(), zonaAtual, extrairQtd(movs)));
        }
        return out;
    }

    /** Puxa a quantidade de registros da observacao da ingestao  */
    private Long extrairQtd(List<MovimentacaoZona> movs) {
        for (MovimentacaoZona m : movs) {
            if (m.getObservacao() == null) {
                continue;
            }
            Matcher mt = QTD.matcher(m.getObservacao());
            if (mt.find()) {
                try {
                    return Long.parseLong(mt.group(1).replace(".", ""));
                } catch (NumberFormatException ignored) {
                    // formato inesperado -> ignora
                }
            }
        }
        return null;
    }
}