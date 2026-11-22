package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ResultadoIsapDetalhe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResultadoIsapDetalheRepository extends JpaRepository<ResultadoIsapDetalhe, Long> {

    @Query("""
        SELECT d FROM ResultadoIsapDetalhe d
         WHERE d.imovel.id = :imovelId
           AND d.versao.id = :versaoId
         ORDER BY d.areaHa DESC, d.categoria ASC, d.nomeFeicao ASC
        """)
    List<ResultadoIsapDetalhe> findPorImovelEVersao(
            @Param("imovelId") Long imovelId,
            @Param("versaoId") Long versaoId);
}