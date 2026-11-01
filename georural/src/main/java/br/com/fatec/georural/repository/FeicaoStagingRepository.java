package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.FeicaoStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeicaoStagingRepository extends JpaRepository<FeicaoStaging, Long> {

    long countByExecucao_Id(Long execucaoId);

    /** Limpeza da area de pouso ao fim da etapa. */
    @Modifying
    @Query("DELETE FROM FeicaoStaging f WHERE f.execucao.id = :execucaoId")
    int apagarPorExecucao(@Param("execucaoId") Long execucaoId);
}
