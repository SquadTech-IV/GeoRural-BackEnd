<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Quarentena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuarentenaRepository extends JpaRepository<Quarentena, Long> {

    /** Total de registros em quarentena de uma execucao, em uma dada situacao*/
    @Query("select count(q) from Quarentena q " +
            "where q.validacao.execucao.id = :exeId and q.situacao = :situacao")
    long contarPorExecucaoESituacao(@Param("exeId") Long exeId, @Param("situacao") String situacao);

    /** Agrupa a quarentena de uma execucao por regra de qualidade */
    @Query("select r.nome, r.tipo, r.severidade, count(q) " +
            "from Quarentena q join q.regra r " +
            "where q.validacao.execucao.id = :exeId and q.situacao = :situacao " +
            "group by r.nome, r.tipo, r.severidade " +
            "order by count(q) desc")
    List<Object[]> agruparRegrasVioladas(@Param("exeId") Long exeId, @Param("situacao") String situacao);
}
=======
package br.com.fatec.georural.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.Quarentena;
import java.util.List;

public interface QuarentenaRepository extends JpaRepository<Quarentena, Long> {
    List<Quarentena> findByValidacao_Id(Long validacaoId);
}
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
