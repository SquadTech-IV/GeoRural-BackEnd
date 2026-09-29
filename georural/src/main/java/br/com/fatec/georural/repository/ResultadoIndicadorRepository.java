package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.Versao;

import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.atn.SemanticContext.AND;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResultadoIndicadorRepository extends JpaRepository<ResultadoIndicador, Long> {
    Optional <ResultadoIndicador> findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(Long imovelId, Indicador indicador);

    boolean existsByImovelIdAndIndicadorAndVersao(Long imovelId, Indicador indicador, Versao versao);

    @Query("""
        SELECT r
        FROM ResultadoIndicador r
        JOIN r.versao v
        WHERE r.indicador.sigla = 'IAE'
        AND v.numero = (
            SELECT MAX(v2.numero)
            FROM ResultadoIndicador r2
            JOIN r2.versao v2
            WHERE r2.imovel.id = r.imovel.id
                AND r2.indicador.sigla = 'IAE'
        )
    """)
    List<ResultadoIndicador> listarImoveisComIAE();
}
