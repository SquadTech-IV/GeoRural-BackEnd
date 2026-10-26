package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Quarentena;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuarentenaRepository extends JpaRepository<Quarentena, Long> {

    List<Quarentena> findByValidacao_Id(Long validacaoId);

    @Query("""
        select count(q) from Quarentena q
            where q.validacao.execucao.id = :exeId and q.situacao = :situacao
        """)
    long contarPorExecucaoESituacao(@Param("exeId") Long exeId, @Param("situacao") String situacao);

    @Query("""
        select r.nome, r.tipo, r.severidade, count(q) 
            from Quarentena q join q.regra r 
            where q.validacao.execucao.id = :exeId and q.situacao = :situacao 
                group by r.nome, r.tipo, r.severidade 
                order by count(q) desc
        """)
    List<Object[]> agruparRegrasVioladas(@Param("exeId") Long exeId, @Param("situacao") String situacao);

    @Query(value = """
        select q from Quarentena q
            left join q.validacao v
            left join v.execucao e
            left join v.arquivoBruto a
            left join q.regra r
        where (:conjuntoId is null or a.conjunto.id = :conjuntoId)
          and (:execucaoId is null or e.id = :execucaoId)
          and (:situacao is null or q.situacao = :situacao)
        """,
        countQuery = """
        select count(q) from Quarentena q
            left join q.validacao v
            left join v.arquivoBruto a
            left join v.execucao e
        where (:conjuntoId is null or a.conjunto.id = :conjuntoId)
          and (:execucaoId is null or e.id = :execucaoId)
          and (:situacao is null or q.situacao = :situacao)
        """)
    Page<Quarentena> consultarQuarentenaPaginada(
            @Param("conjuntoId") Long conjuntoId,
            @Param("execucaoId") Long execucaoId,
            @Param("situacao") String situacao,
            Pageable pageable);
}