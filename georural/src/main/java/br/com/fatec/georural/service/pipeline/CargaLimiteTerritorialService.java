package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.entity.LimiteTerritorial;
import br.com.fatec.georural.repository.LimiteTerritorialRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CargaLimiteTerritorialService {

    private static final String TIPO = "UF";
    private static final String CODIGO = "PR";
    private static final String NOME = "Paraná";

    private final LimiteTerritorialRepository repository;

    @PersistenceContext
    private EntityManager em;

    /** Quantos municipios do PR ja foram ingeridos. */
    public long municipiosDisponiveis() {
        Number n = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM MUNICIPIO WHERE mun_uf = 'PR' AND mun_geometria IS NOT NULL")
                .getSingleResult();
        return n == null ? 0L : n.longValue();
    }

    public boolean jaCarregado() {
        return repository.existsByTipoAndCodigo(TIPO, CODIGO);
    }


    @Transactional
    public LimiteTerritorial derivarDaMalhaMunicipal() {
        long municipios = municipiosDisponiveis();
        if (municipios == 0) {
            throw new MalhaMunicipalAusenteException();
        }

        repository.findByTipoAndCodigo(TIPO, CODIGO).ifPresent(repository::delete);
        em.flush();

        em.createNativeQuery("""
                INSERT INTO LIMITE_TERRITORIAL (lim_tipo, lim_codigo, lim_nome, lim_geometria)
                VALUES (?, ?, ?,
                        (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(m.mun_geometria, 0.05))
                           FROM MUNICIPIO m
                          WHERE m.mun_uf = 'PR' AND m.mun_geometria IS NOT NULL))
                """)
                .setParameter(1, TIPO)
                .setParameter(2, CODIGO)
                .setParameter(3, NOME)
                .executeUpdate();

        LimiteTerritorial salvo = repository.findByTipoAndCodigo(TIPO, CODIGO)
                .orElseThrow(() -> new IllegalStateException(
                        "A uniao dos municipios nao produziu geometria para o limite do PR."));

        log.info("Limite do Parana derivado da uniao de {} municipios (id={}). "
                + "PKG_GEO.recorte_uf ja pode ser usado.", municipios, salvo.getId());
        return salvo;
    }

    /** Carrega se ainda nao existir e se houver malha municipal. */
    @Transactional
    public boolean carregarSeNecessario() {
        if (jaCarregado()) {
            log.info("Limite do Parana ja esta em LIMITE_TERRITORIAL.");
            return false;
        }
        long municipios = municipiosDisponiveis();
        if (municipios == 0) {
            log.info("Limite do Parana ainda nao disponivel: a malha municipal do IBGE "
                    + "(conjunto do tipo MALHA_MUNICIPAL) ainda nao foi ingerida. "
                    + "O recorte so fica disponivel depois dessa ingestao.");
            return false;
        }
        derivarDaMalhaMunicipal();
        return true;
    }

    /** Falta de dado, nao falha tecnica: a mensagem diz o que fazer. */
    public static class MalhaMunicipalAusenteException extends RuntimeException {
        public MalhaMunicipalAusenteException() {
            super("Nenhum municipio do PR foi ingerido ainda. Ingira a malha municipal do IBGE "
                    + "(conjunto do tipo MALHA_MUNICIPAL) e chame este endpoint de novo.");
        }
    }
}
