package br.com.fatec.georural.service.pipeline.inventario;

import br.com.fatec.georural.dto.response.inventario.ArquivoInventarioResponse;
import br.com.fatec.georural.dto.response.inventario.MembroInventarioResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.service.pipeline.perfil.PerfilCamada;
import br.com.fatec.georural.service.pipeline.perfil.RegistroPerfis;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// monta a visao de "o que ja foi processado e o que nao foi"
@Service
@RequiredArgsConstructor
public class ConsultaInventarioService {

    private static final String PROCESSADO = "PROCESSADO";

    private final ArquivoBrutoRepository arquivoRepository;
    private final RegistroPerfis registroPerfis;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<ArquivoInventarioResponse> doConjunto(Long conjuntoId) {
        Map<Long, LocalDateTime> tratamentos = quandoForamTratados(conjuntoId);

        return arquivoRepository.findByConjunto_IdAndPaiIsNullOrderByDataUploadDesc(conjuntoId)
                .stream()
                .map(pai -> montar(pai, tratamentos))
                .toList();
    }

    private ArquivoInventarioResponse montar(ArquivoBruto pai, Map<Long, LocalDateTime> tratamentos) {
        List<ArquivoBruto> filhos = arquivoRepository.findByPai_IdOrderByCaminhoInternoAsc(pai.getId());
        Optional<PerfilCamada> doConjunto = registroPerfis.buscar(tipoDoConjunto(pai));

        // arquivo que nao e pacote aparece como uma camada unica, a dele mesmo
        List<ArquivoBruto> camadas = filhos.isEmpty() ? List.of(pai) : filhos;

        List<MembroInventarioResponse> membros = camadas.stream()
                .map(f -> {
                    Optional<PerfilCamada> perfil = registroPerfis
                            .porNomeDeCamada(nomeParaPerfil(f))
                            .or(() -> doConjunto);

                    return new MembroInventarioResponse(
                            f.getId(), f.getNome(), f.getCaminhoInterno(), f.getFormatoDetectado(),
                            f.getTamanhoBytes(), f.getSrsDeclarado(), f.getSrsUtilizado(),
                            origemDoSrs(f),
                            perfil.map(PerfilCamada::tipoConjunto).orElse(null),
                            perfil.map(PerfilCamada::rotulo).orElse(null),
                            f.getStatus(), PROCESSADO.equals(f.getStatus()),
                            tratamentos.get(f.getId()));
                })
                .toList();

        int processados = (int) membros.stream().filter(MembroInventarioResponse::processado).count();

        return new ArquivoInventarioResponse(
                pai.getId(), pai.getNome(), pai.getFormatoDetectado(), pai.getCanalEntrada(),
                pai.getCompetencia(), pai.getTamanhoBytes(), pai.getHash(), pai.getStatus(),
                !filhos.isEmpty(), membros.size(), processados, pai.getDataUpload(), membros);
    }

    // o normalizador do registro ja tira caminho e extensao
    private String nomeParaPerfil(ArquivoBruto a) {
        String nome = a.getNome();
        return nome != null && !nome.isBlank() ? nome : a.getCaminhoInterno();
    }

    private String tipoDoConjunto(ArquivoBruto a) {
        ConjuntoDados conjunto = a.getConjunto();
        return conjunto != null && conjunto.getTipoConjunto() != null
                ? conjunto.getTipoConjunto().getCodigo() : null;
    }

    // nulo em srsDeclarado significa que o arquivo foi omisso e o SRC foi suposto
    private String origemDoSrs(ArquivoBruto a) {
        return a.getSrsDeclarado() != null ? "LIDO DO ARQUIVO" : "SUPOSTO";
    }

    // quando cada arquivo do conjunto concluiu a etapa de tratamento
    @SuppressWarnings("unchecked")
    private Map<Long, LocalDateTime> quandoForamTratados(Long conjuntoId) {
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT e.arq_bruto_id, MAX(e.exe_fim)
                  FROM EXECUCAO_PIPELINE e
                 WHERE e.con_id = ?
                   AND e.exe_etapa = 'TRATAMENTO'
                   AND e.arq_bruto_id IS NOT NULL
                 GROUP BY e.arq_bruto_id
                """).setParameter(1, conjuntoId).getResultList();

        Map<Long, LocalDateTime> mapa = new HashMap<>();
        for (Object[] linha : linhas) {
            if (linha[0] == null) continue;
            Long id = ((Number) linha[0]).longValue();
            LocalDateTime fim = linha[1] instanceof Timestamp ts ? ts.toLocalDateTime() : null;
            mapa.put(id, fim);
        }
        return mapa;
    }
}
