<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecucaoPipelineRepository extends JpaRepository<ExecucaoPipeline, Long> {
    List<ExecucaoPipeline> findByConjunto_IdOrderByInicioAsc(Long conjuntoId);

    List<ExecucaoPipeline> findByDagRunIdOrderByInicioAsc(String dagRunId);
}
=======
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecucaoPipelineRepository extends JpaRepository<ExecucaoPipeline, Long> {
    List<ExecucaoPipeline> findByConjunto_IdOrderByInicioAsc(Long conjuntoId);
}
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
