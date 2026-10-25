<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.RegraQualidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegraQualidadeRepository extends JpaRepository<RegraQualidade, Long> {

    /** Regras ativas. */
    List<RegraQualidade> findByAtiva(String ativa);
}
=======
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.RegraQualidade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegraQualidadeRepository extends JpaRepository<RegraQualidade, Long> {
}
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
