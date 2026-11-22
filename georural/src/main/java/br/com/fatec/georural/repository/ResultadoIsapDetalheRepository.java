package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ResultadoIsapDetalhe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResultadoIsapDetalheRepository extends JpaRepository<ResultadoIsapDetalhe, Long> {

    @Query("""
        SELECT d FROM ResultadoIsapDetalhe d
          JOIN FETCH d.areaProtegida a
          JOIN FETCH d.versaoCamada vc
         WHERE d.resumo.id = :resumoId
         ORDER BY d.areaSobrepostaHa DESC, d.categoria ASC, d.nomeArea ASC
        """)
    List<ResultadoIsapDetalhe> findPorResumoId(@Param("resumoId") Long resumoId);
}