package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.dto.response.inventario.PreviaArquivoResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class PreviaArquivoService {

    private static final int LIMITE_PADRAO = 20;
    private static final int LIMITE_MAXIMO = 200;

    private final ArquivoBrutoRepository arquivoRepository;
    private final ArmazenamentoZonaPort armazenamento;
    private final LeitorFactory leitorFactory;

    /** Sinaliza "ja tenho amostra suficiente"; nao e erro. */
    private static class PreviaCompleta extends RuntimeException {
        PreviaCompleta() {
            super(null, null, false, false);
        }
    }

    @Transactional(readOnly = true)
    public PreviaArquivoResponse previa(Long arquivoBrutoId, Integer limiteSolicitado) {
        ArquivoBruto arquivo = arquivoRepository.findById(arquivoBrutoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Arquivo bruto nao encontrado: " + arquivoBrutoId));

        int limite = Math.min(limiteSolicitado == null || limiteSolicitado <= 0
                ? LIMITE_PADRAO : limiteSolicitado, LIMITE_MAXIMO);

        FormatoArquivo formato = formatoDe(arquivo);
        if (formato == FormatoArquivo.GEOTIFF) {
            return vazia(arquivo, formato,
                    "GeoTIFF e raster: nao tem feicoes para listar. O arquivo fica catalogado "
                            + "e entra no calculo quando o indicador sobre raster (ICV) for implementado.");
        }
        if (!leitorFactory.temLeitor(formato)) {
            return vazia(arquivo, formato,
                    "Ainda nao existe leitor para o formato " + formato.rotulo() + ".");
        }

        Integer srs = srsDe(arquivo);
        String path = arquivo.getPathArmazenamento();
        ContextoLeitura ctx = new ContextoLeitura(
                formato, arquivo.getNome(), arquivo.getCaminhoInterno(),
                () -> armazenamento.ler(Zona.BRUTA, path), srs);

        Set<String> colunas = new LinkedHashSet<>();
        List<Map<String, String>> amostra = new ArrayList<>();
        String[] tipoGeometria = {null};
        long[] lidas = {0};

        try {
            leitorFactory.para(formato).ler(ctx, feicao -> {
                lidas[0]++;
                if (feicao.atributos() != null) {
                    colunas.addAll(feicao.atributos().keySet());
                }
                amostra.add(comoTexto(feicao.atributos()));
                if (tipoGeometria[0] == null && feicao.temGeometria()) {
                    tipoGeometria[0] = feicao.geometria().getGeometryType();
                }
                if (amostra.size() >= limite) {
                    throw new PreviaCompleta();
                }
            });
        } catch (PreviaCompleta parada) {
            // amostra cheia: encerramento normal
            return montar(arquivo, formato, srs, tipoGeometria[0], lidas[0], true, colunas, amostra, null);
        } catch (RuntimeException e) {
            log.warn("Previa do arquivo {} falhou: {}", arquivoBrutoId, e.getMessage());
            return montar(arquivo, formato, srs, null, 0, false, colunas, amostra,
                    "Nao foi possivel ler o arquivo: " + e.getMessage());
        }

        return montar(arquivo, formato, srs, tipoGeometria[0], lidas[0], false, colunas, amostra,
                lidas[0] == 0 ? "O arquivo foi lido mas nao tem nenhuma feicao." : null);
    }

    /** Formato ja detectado na ingestao; se o registro e antigo, detecta na hora. */
    private FormatoArquivo formatoDe(ArquivoBruto arquivo) {
        String detectado = arquivo.getFormatoDetectado();
        if (detectado != null && !detectado.isBlank()) {
            try {
                return FormatoArquivo.valueOf(detectado.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // cai para o formato do cadastro
            }
        }
        String declarado = arquivo.getFormato();
        if (declarado != null) {
            try {
                return FormatoArquivo.valueOf(declarado.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // desconhecido
            }
        }
        return FormatoArquivo.DESCONHECIDO;
    }

    private Integer srsDe(ArquivoBruto arquivo) {
        if (arquivo.getSrsUtilizado() != null) {
            return arquivo.getSrsUtilizado();
        }
        if (arquivo.getSrsDeclarado() != null) {
            return arquivo.getSrsDeclarado();
        }
        return arquivo.getConjunto() != null && arquivo.getConjunto().getSrs() != null
                ? arquivo.getConjunto().getSrs() : 4674;
    }


    private Map<String, String> comoTexto(Map<String, Object> atributos) {
        Map<String, String> linha = new LinkedHashMap<>();
        if (atributos != null) {
            atributos.forEach((k, v) -> linha.put(k, valorTexto(v)));
        }
        return linha;
    }

    private String valorTexto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Geometry) {
            return "[geometria]";
        }
        String texto = valor.toString();
        return texto.length() > 300 ? texto.substring(0, 300) + "..." : texto;
    }

    private PreviaArquivoResponse vazia(ArquivoBruto arquivo, FormatoArquivo formato, String observacao) {
        return montar(arquivo, formato, srsDe(arquivo), null, 0, false,
                new LinkedHashSet<>(), List.of(), observacao);
    }

    private PreviaArquivoResponse montar(ArquivoBruto arquivo, FormatoArquivo formato, Integer srs,
                                         String tipoGeometria, long lidas, boolean truncado,
                                         Set<String> colunas, List<Map<String, String>> amostra,
                                         String observacao) {
        return new PreviaArquivoResponse(
                arquivo.getId(), arquivo.getNome(), arquivo.getCaminhoInterno(),
                formato.name(), formato.rotulo(), srs,
                arquivo.getSrsDeclarado() != null ? "LIDO DO ARQUIVO" : "SUPOSTO",
                tipoGeometria, lidas, truncado,
                List.copyOf(colunas), amostra, observacao);
    }
}
