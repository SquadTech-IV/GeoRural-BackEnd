<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.EntradaExecucao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntradaExecucaoRepository extends JpaRepository<EntradaExecucao, Long> {
    List<EntradaExecucao> findByExecucao_Id(Long execucaoId);
}
=======
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.EntradaExecucao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntradaExecucaoRepository extends JpaRepository<EntradaExecucao, Long> {
}
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
