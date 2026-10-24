<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Validacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ValidacaoRepository extends JpaRepository<Validacao, Long> {

    /** Todas as validacoes vinculadas a uma execucao do pipeline. */
    List<Validacao> findByExecucao_Id(Long execucaoId);

    Optional<Validacao> findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(Long arquivoBrutoId);
}
=======
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Validacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ValidacaoRepository extends JpaRepository<Validacao, Long> {
    List<Validacao> findByExecucao_Id(Long execucaoId);
    Optional<Validacao> findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(Long arquivoBrutoId);
}
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
