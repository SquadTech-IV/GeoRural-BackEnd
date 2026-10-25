package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Transformacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransformacaoRepository extends JpaRepository<Transformacao, Long> {
    List<Transformacao> findByExecucao_IdOrderByOrdemAsc(Long execucaoId);

    @Query(value = "SELECT NVL(MAX(TRA_ORDEM), 0) FROM TRANSFORMACAO WHERE EXE_ID = :exeId", nativeQuery = true)
    long maxOrdem(@Param("exeId") Long exeId);
}
